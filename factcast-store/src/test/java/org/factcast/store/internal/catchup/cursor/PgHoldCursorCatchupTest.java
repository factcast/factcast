/*
 * Copyright © 2017-2026 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.store.internal.catchup.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;

@ExtendWith(MockitoExtension.class)
class PgHoldCursorCatchupTest {

  @Mock(strictness = Mock.Strictness.LENIENT)
  StoreConfigurationProperties props;

  @Mock(strictness = Mock.Strictness.LENIENT)
  SubscriptionRequestTO req;

  @Mock @NonNull CurrentStatementHolder statementHolder;
  @Mock @NonNull ServerPipeline pipeline;

  @Mock(strictness = Mock.Strictness.LENIENT)
  @NonNull
  PgMetrics metrics;

  @Mock(strictness = Mock.Strictness.LENIENT)
  @NonNull
  PlatformTransactionManager txMgr;

  @Mock @NonNull AtomicLong serial;
  @Mock DataSource ds;

  PgHoldCursorCatchup underTest;

  @BeforeEach
  void setup() {
    underTest =
        spy(
            new PgHoldCursorCatchup(
                props,
                metrics,
                req,
                pipeline,
                serial,
                statementHolder,
                ds,
                txMgr,
                PgCatchupFactory.Phase.PHASE_1));
  }

  @Nested
  class WhenRunning {

    @SneakyThrows
    @Test
    void flushesClearsAndClosesCursor() {
      Connection connection = mock(Connection.class, RETURNS_DEEP_STUBS);
      doReturn(connection).when(ds).getConnection();
      doReturn("cursor_1").when(underTest).createCursorName();
      // Use any() for connection to ensure it matches, even if wrapped
      doReturn(true).when(underTest).fetch(any(), anyString());

      underTest.run();

      verify(statementHolder).clear();
      verify(pipeline).process(argThat(Signal::indicatesFlush));
    }

    @SneakyThrows
    @Test
    void doesNotFlushIfFetchReturnsFalse() {
      Connection connection = mock(Connection.class, RETURNS_DEEP_STUBS);
      doReturn(connection).when(ds).getConnection();
      doReturn(false).when(underTest).fetch(any(), anyString());

      underTest.run();

      verify(statementHolder).clear();
      verify(pipeline, never()).process(argThat(Signal::indicatesFlush));
    }
  }

  @Nested
  class WhenFetching {
    @Mock Connection connection;
    @Mock Timer timer;
    @Mock Timer.Sample sample;

    @BeforeEach
    void setup() {
      when(props.getPageSize()).thenReturn(47);
      when(props.getChunkSize()).thenReturn(500);
      when(req.specs()).thenReturn(java.util.Collections.emptyList());
      when(serial.get()).thenReturn(0L);
      when(metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, true)).thenReturn(timer);
      when(metrics.startSample()).thenReturn(sample);
    }

    @Test
    @SneakyThrows
    void noTransactionIfOnlyOneRowToFetch() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      doNothing().when(underTest).closeCursor(any(), anyString());
      doReturn(12).when(underTest).fetchChunk(any(), anyString(), any(), any(), any(), any());

      underTest.fetch(connection, "cursor_1");

      // declares
      verify(underTest).declareCursor(same(connection), anyString(), any(), any());
      verify(txMgr, never()).getTransaction(any());
      verify(underTest, times(1))
          .fetchChunk(same(connection), anyString(), any(), any(), any(), any());
    }

    @Test
    @SneakyThrows
    void declaresCursorCommitsAndFetchesUntilEmpty() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      doNothing().when(underTest).closeCursor(any(), anyString());
      doReturn(props.getChunkSize(), props.getChunkSize(), 0)
          .when(underTest)
          .fetchChunk(any(), anyString(), any(), any(), any(), any());
      verify(txMgr, never()).getTransaction(any());

      underTest.fetch(connection, "cursor_1");

      // declares
      verify(underTest).declareCursor(same(connection), anyString(), any(), any());
      verify(txMgr, times(1)).commit(any());
      verify(underTest, times(3))
          .fetchChunk(same(connection), anyString(), any(), any(), any(), any());
    }

    @Test
    @SneakyThrows
    void returnsImmediatelyWhenCancelled() {
      when(statementHolder.wasCanceled()).thenReturn(true);

      underTest.fetch(connection, "cursor_1");

      verify(underTest, never()).declareCursor(any(), anyString(), any(), any());
      verify(underTest, never()).fetchChunk(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    @SneakyThrows
    void returnsImmediatelyWhenCancelledAfterDeclaring() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      when(statementHolder.wasCanceled()).thenReturn(false, true);

      underTest.fetch(connection, "cursor_1");

      verify(underTest).declareCursor(any(), anyString(), any(), any());
      verify(underTest, never()).fetchChunk(any(), anyString(), any(), any(), any(), any());
    }

    @Test
    @SneakyThrows
    void returnsFalseWhenEmpty() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      doNothing().when(underTest).closeCursor(any(), anyString());
      doReturn(0).when(underTest).fetchChunk(any(), anyString(), any(), any(), any(), any());

      boolean result = underTest.fetch(connection, "cursor_1");

      assertThat(result).isFalse();
    }

    @Test
    @SneakyThrows
    void returnsTrueWhenUnderChunkSize() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      doNothing().when(underTest).closeCursor(any(), anyString());
      doReturn(10).when(underTest).fetchChunk(any(), anyString(), any(), any(), any(), any());

      boolean result = underTest.fetch(connection, "cursor_1");

      assertThat(result).isTrue();
    }

    @Test
    @SneakyThrows
    void returnsTrueWhenCancelledAfterFirstChunk() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      doNothing().when(underTest).closeCursor(any(), anyString());
      doReturn(props.getChunkSize())
          .when(underTest)
          .fetchChunk(any(), anyString(), any(), any(), any(), any());
      when(statementHolder.wasCanceled()).thenReturn(false, false, true);

      boolean result = underTest.fetch(connection, "cursor_1");

      assertThat(result).isTrue();
      verify(txMgr, never()).getTransaction(any());
    }

    @Test
    @SneakyThrows
    void handlesTransactionExceptionWithSqlCause() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      doNothing().when(underTest).closeCursor(any(), anyString());
      doReturn(props.getChunkSize())
          .when(underTest)
          .fetchChunk(any(), anyString(), any(), any(), any(), any());

      SQLException sqlEx = new SQLException("foo");
      TransactionException txEx =
          new TransactionException("bar", sqlEx) {
            private static final long serialVersionUID = 1L;
          };
      when(txMgr.getTransaction(any())).thenThrow(txEx);

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> underTest.fetch(connection, "cursor_1"))
          .isSameAs(sqlEx);
    }

    @Test
    @SneakyThrows
    void handlesTransactionExceptionWithoutSqlCause() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      doNothing().when(underTest).closeCursor(any(), anyString());
      doReturn(props.getChunkSize())
          .when(underTest)
          .fetchChunk(any(), anyString(), any(), any(), any(), any());

      TransactionException txEx =
          new TransactionException("bar") {
            private static final long serialVersionUID = 1L;
          };
      when(txMgr.getTransaction(any())).thenThrow(txEx);

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> underTest.fetch(connection, "cursor_1"))
          .isSameAs(txEx);
    }
  }

  @Nested
  class WhenDeclaringCursorAndFetchingChunks {
    @Mock Connection connection;
    @Mock PreparedStatement declare;
    @Mock Statement fetch;
    @Mock ResultSet rs;
    @Mock Timer timer;
    @Mock Timer.Sample sample;

    @Test
    @SneakyThrows
    void declareCursorBindsAndExecutes() {
      when(connection.prepareStatement(anyString())).thenReturn(declare);
      var queryBuilder = mock(PgQueryBuilder.class);
      AtomicLong fromSerial = new AtomicLong();
      when(queryBuilder.createStatementSetter(fromSerial)).thenReturn(p -> p.setLong(1, 3L));

      underTest.declareCursor(connection, "foo", queryBuilder, fromSerial);

      verify(statementHolder).statement(declare, true);
      verify(declare).setLong(1, 3L);
      verify(declare).execute();
    }

    @Test
    @SneakyThrows
    void fetchChunkProcessesFactsAndStopsTimerOnFirstRow() {
      when(props.getPageSize()).thenReturn(23);
      when(connection.createStatement()).thenReturn(fetch);
      when(fetch.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(true, false);
      when(rs.getString(PgConstants.ALIAS_ID)).thenReturn(java.util.UUID.randomUUID().toString());
      when(rs.getString(PgConstants.ALIAS_AGGID)).thenReturn("[]");
      when(rs.getString(PgConstants.ALIAS_TYPE)).thenReturn("t");
      when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("ns");
      when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs.getInt(PgConstants.COLUMN_VERSION)).thenReturn(1);
      when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(7L);

      int rows =
          underTest.fetchChunk(
              connection,
              "FETCH FORWARD 23 FROM cursor_1",
              new org.factcast.store.internal.rowmapper.PgFactExtractor(serial),
              sample,
              timer,
              new AtomicBoolean(false));

      assertThat(rows).isEqualTo(1);
      verify(fetch).setFetchSize(23);
      verify(statementHolder).statement(fetch, false);
      verify(sample).stop(timer);
      verify(pipeline).process(any());
    }

    @Test
    @SneakyThrows
    void fetchChunkSwallowsCancellationDuringExecuteQuery() {
      when(props.getPageSize()).thenReturn(23);
      when(connection.createStatement()).thenReturn(fetch);
      PSQLException cancelled = mock(PSQLException.class);
      when(fetch.executeQuery(anyString())).thenThrow(cancelled);
      when(statementHolder.wasCanceled()).thenReturn(true);

      int rows =
          underTest.fetchChunk(
              connection,
              "FETCH FORWARD 23 FROM cursor_1",
              new org.factcast.store.internal.rowmapper.PgFactExtractor(serial),
              sample,
              timer,
              new AtomicBoolean(false));

      assertThat(rows).isEqualTo(0);
      verify(statementHolder).statement(fetch, false);
    }

    @Test
    @SneakyThrows
    void fetchChunkSwallowsCancellationDuringRsNextAndReturnsProcessedRows() {
      when(props.getPageSize()).thenReturn(23);
      when(connection.createStatement()).thenReturn(fetch);
      when(fetch.executeQuery(anyString())).thenReturn(rs);
      PSQLException cancelled = mock(PSQLException.class);
      when(rs.next()).thenReturn(true).thenThrow(cancelled);
      when(rs.getString(PgConstants.ALIAS_ID)).thenReturn(java.util.UUID.randomUUID().toString());
      when(rs.getString(PgConstants.ALIAS_AGGID)).thenReturn("[]");
      when(rs.getString(PgConstants.ALIAS_TYPE)).thenReturn("t");
      when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("ns");
      when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs.getInt(PgConstants.COLUMN_VERSION)).thenReturn(1);
      when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(7L);
      when(statementHolder.wasCanceled()).thenReturn(false, true);

      int rows =
          underTest.fetchChunk(
              connection,
              "FETCH FORWARD 23 FROM cursor_1",
              new org.factcast.store.internal.rowmapper.PgFactExtractor(serial),
              sample,
              timer,
              new AtomicBoolean(false));

      assertThat(rows).isEqualTo(1);
      verify(pipeline).process(any());
    }

    @Test
    @SneakyThrows
    void fetchChunkStopsIfRsIsClosed() {
      when(connection.createStatement()).thenReturn(fetch);
      when(fetch.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(true);
      when(rs.isClosed()).thenReturn(true);

      int rows =
          underTest.fetchChunk(
              connection,
              "FETCH FORWARD 23 FROM cursor_1",
              new org.factcast.store.internal.rowmapper.PgFactExtractor(serial),
              sample,
              timer,
              new AtomicBoolean(false));

      assertThat(rows).isEqualTo(0);
    }

    @Test
    @SneakyThrows
    void fetchChunkThrowsIfNonCancellationPsqlException() {
      when(connection.createStatement()).thenReturn(fetch);
      PSQLException ex = new PSQLException("foo", org.postgresql.util.PSQLState.UNKNOWN_STATE);
      when(fetch.executeQuery(anyString())).thenThrow(ex);
      when(statementHolder.wasCanceled()).thenReturn(false);

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  underTest.fetchChunk(
                      connection,
                      "FETCH FORWARD 23 FROM cursor_1",
                      new org.factcast.store.internal.rowmapper.PgFactExtractor(serial),
                      sample,
                      timer,
                      new AtomicBoolean(false)))
          .isSameAs(ex);
    }

    @Test
    @SneakyThrows
    void fetchChunkThrowsIfNonCancellationPsqlExceptionDuringRowMapping() {
      when(connection.createStatement()).thenReturn(fetch);
      when(fetch.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(true);
      PSQLException ex = new PSQLException("foo", org.postgresql.util.PSQLState.UNKNOWN_STATE);
      // Row mapping uses rs.getString etc.
      when(rs.getString(anyString())).thenThrow(ex);
      when(statementHolder.wasCanceled()).thenReturn(false);

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  underTest.fetchChunk(
                      connection,
                      "FETCH FORWARD 23 FROM cursor_1",
                      new org.factcast.store.internal.rowmapper.PgFactExtractor(serial),
                      sample,
                      timer,
                      new AtomicBoolean(false)))
          .isSameAs(ex);
    }
  }

  @Test
  void testSqlHelpers() {
    when(props.getPageSize()).thenReturn(17);

    assertThat(underTest.createFetchSql("cursor_1"))
        .startsWith("SELECT ")
        .endsWith(" FROM fetchFactsFrom('cursor_1')");
    assertThat(underTest.createCloseCursorSql("cursor_1")).isEqualTo("CLOSE cursor_1");
    assertThat(underTest.createCursorName()).startsWith("catchup_");
  }

  @Test
  void logIfAboveThresholdEmitsInfo() {
    try (LogCaptor logCaptor = LogCaptor.forClass(PgHoldCursorCatchup.class)) {
      var elapsed = PgHoldCursorCatchup.FIRST_ROW_FETCHING_THRESHOLD.plusSeconds(2);

      underTest.logIfAboveThreshold(elapsed);

      assertThat(logCaptor.getInfoLogs()).isNotEmpty();
      assertThat(logCaptor.getInfoLogs().get(0))
          .contains(
              "took " + elapsed.toMillis() + "ms until the held cursor returned the first result");
    }
  }

  @Test
  void logIfBelowThresholdDoesNotEmitInfo() {
    try (LogCaptor logCaptor = LogCaptor.forClass(PgHoldCursorCatchup.class)) {
      var elapsed = PgHoldCursorCatchup.FIRST_ROW_FETCHING_THRESHOLD.minusSeconds(1);

      underTest.logIfAboveThreshold(elapsed);

      assertThat(logCaptor.getInfoLogs()).isEmpty();
    }
  }

  @Test
  @SneakyThrows
  void closeCursorSwallowsException() {
    Connection connection = mock(Connection.class);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(connection.prepareStatement(anyString())).thenReturn(ps);
    when(ps.execute()).thenThrow(new SQLException("boom"));

    try (LogCaptor logCaptor = LogCaptor.forClass(PgHoldCursorCatchup.class)) {
      underTest.closeCursor(connection, "foo");
      assertThat(logCaptor.getWarnLogs()).isNotEmpty();
      assertThat(logCaptor.getWarnLogs().get(0)).contains("While closing held cursor");
    }
  }
}
