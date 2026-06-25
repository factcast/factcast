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
package org.factcast.store.internal.catchup.chunkedwithhold;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@ExtendWith(MockitoExtension.class)
class PgChunkedWithHoldCursorCatchupTest {

  @Mock(strictness = Mock.Strictness.LENIENT)
  @NonNull
  StoreConfigurationProperties props;

  @Mock(strictness = Mock.Strictness.LENIENT)
  SubscriptionRequestTO req;

  @Mock @NonNull CurrentStatementHolder statementHolder;
  @Mock @NonNull ServerPipeline pipeline;

  @Mock(strictness = Mock.Strictness.LENIENT)
  @NonNull
  PgMetrics metrics;

  @Mock @NonNull AtomicLong serial;

  @Mock(strictness = Mock.Strictness.LENIENT)
  SingleConnectionDataSource ds;

  PgCatchupFactory.Phase phase = PgCatchupFactory.Phase.PHASE_1;

  PgChunkedWithHoldCursorCatchup underTest;

  @BeforeEach
  void setup() {
    underTest =
        spy(
            new PgChunkedWithHoldCursorCatchup(
                props, metrics, req, pipeline, serial, statementHolder, ds, phase));
  }

  @Nested
  class WhenRunning {

    @SneakyThrows
    @Test
    void flushesWhenFetchReturnedRows() {
      doReturn(true).when(underTest).fetch(anyString());

      underTest.run();

      ArgumentCaptor<Signal> sigCap = ArgumentCaptor.forClass(Signal.class);
      verify(pipeline).process(sigCap.capture());
      assertThat(sigCap.getValue().indicatesFlush()).isTrue();
    }

    @SneakyThrows
    @Test
    void doesNotFlushWhenFetchWasEmpty() {
      doReturn(false).when(underTest).fetch(anyString());

      underTest.run();

      verifyNoInteractions(pipeline);
    }

    @SneakyThrows
    @Test
    void passesGeneratedCursorNameToFetch() {
      doReturn(false).when(underTest).fetch(anyString());

      underTest.run();

      ArgumentCaptor<String> cursorCap = ArgumentCaptor.forClass(String.class);
      verify(underTest).fetch(cursorCap.capture());
      assertThat(cursorCap.getValue()).startsWith("catchup_");
    }
  }

  @Nested
  class WhenBuildingSql {

    @Test
    void createFetchSqlReferencesCursor() {
      String sql = underTest.createFetchSql("catchup_abc");

      assertThat(sql)
          .contains(PgConstants.PROJECTION_FACT)
          .contains("fetchFactsFrom('catchup_abc')");
    }

    @Test
    void createCloseCursorSqlClosesCursor() {
      assertThat(underTest.createCloseCursorSql("catchup_abc")).isEqualTo("CLOSE catchup_abc");
    }

    @Test
    void createCursorNameIsPrefixedAndUnique() {
      String first = underTest.createCursorName();
      String second = underTest.createCursorName();

      assertThat(first).startsWith("catchup_").doesNotContain("-");
      assertThat(second).startsWith("catchup_");
      assertThat(first).isNotEqualTo(second);
    }
  }

  @Nested
  class WhenLoggingThreshold {

    @Test
    void emitsInfoWhenAboveThreshold() {
      try (LogCaptor logCaptor = LogCaptor.forClass(PgChunkedWithHoldCursorCatchup.class)) {
        underTest.logIfAboveThreshold(
            PgChunkedWithHoldCursorCatchup.FIRST_ROW_FETCHING_THRESHOLD.plusSeconds(2));

        assertThat(logCaptor.getInfoLogs()).isNotEmpty();
      }
    }

    @Test
    void doesNotEmitInfoWhenBelowThreshold() {
      try (LogCaptor logCaptor = LogCaptor.forClass(PgChunkedWithHoldCursorCatchup.class)) {
        underTest.logIfAboveThreshold(java.time.Duration.ofMillis(1));

        assertThat(logCaptor.getInfoLogs()).isEmpty();
      }
    }
  }

  @Nested
  class WhenClosingCursor {

    @SneakyThrows
    @Test
    void executesCloseStatement() {
      Connection conn = mock(Connection.class);
      PreparedStatement ps = mock(PreparedStatement.class);
      when(ds.getConnection()).thenReturn(conn);
      when(conn.prepareStatement("CLOSE catchup_x")).thenReturn(ps);

      underTest.closeCursor("catchup_x");

      verify(ps).execute();
    }

    @SneakyThrows
    @Test
    void swallowsExceptionsWhileClosing() {
      when(ds.getConnection()).thenThrow(new SQLException("boom"));

      assertThatCode(() -> underTest.closeCursor("catchup_x")).doesNotThrowAnyException();
    }
  }

  @Nested
  class WhenDeclaringCursor {

    @SneakyThrows
    @Test
    void preparesAndExecutesDeclareStatement() {
      when(props.getChunkSize()).thenReturn(50);
      Connection conn = mock(Connection.class);
      PreparedStatement ps = mock(PreparedStatement.class);
      when(conn.prepareStatement(anyString())).thenReturn(ps);

      PgQueryBuilder qb = new PgQueryBuilder(Collections.emptyList(), statementHolder);
      qb.serialsOnly();

      underTest.declareCursor(conn, "catchup_x", qb, new AtomicLong(0));

      ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
      verify(conn).prepareStatement(sqlCap.capture());
      assertThat(sqlCap.getValue())
          .contains("DECLARE catchup_x CURSOR WITH HOLD")
          .contains("array_agg");
      verify(statementHolder).statement(ps, true);
      verify(ps).execute();
    }
  }

  @Nested
  class WhenFetchingChunk {

    @SneakyThrows
    @Test
    void mapsRowsAndForwardsThemToPipeline() {
      when(props.getPageSize()).thenReturn(10);
      Connection conn = mock(Connection.class);
      Statement stmt = mock(Statement.class);
      ResultSet rs = mock(ResultSet.class);
      when(ds.getConnection()).thenReturn(conn);
      when(conn.createStatement()).thenReturn(stmt);
      when(stmt.executeQuery(anyString())).thenReturn(rs);
      mockSingleRow(rs, 1L);
      when(statementHolder.wasCanceled()).thenReturn(false);

      Timer timer = mock(Timer.class);
      Timer.Sample sample = mock(Timer.Sample.class);
      AtomicBoolean firstRow = new AtomicBoolean(false);

      int rows =
          underTest.fetchChunk("select 1", new PgFactExtractor(serial), sample, timer, firstRow);

      assertThat(rows).isEqualTo(1);
      assertThat(firstRow).isTrue();
      verify(sample).stop(timer);
      verify(pipeline).process(any());
    }

    @SneakyThrows
    @Test
    void swallowsCancellationWhenQueryThrowsPsqlException() {
      when(props.getPageSize()).thenReturn(10);
      Connection conn = mock(Connection.class);
      Statement stmt = mock(Statement.class);
      when(ds.getConnection()).thenReturn(conn);
      when(conn.createStatement()).thenReturn(stmt);
      when(stmt.executeQuery(anyString()))
          .thenThrow(new PSQLException("canceled", PSQLState.QUERY_CANCELED));
      // the statement was canceled, so the exception must be swallowed
      when(statementHolder.wasCanceled()).thenReturn(true);

      int rows =
          underTest.fetchChunk(
              "select 1",
              new PgFactExtractor(serial),
              mock(Timer.Sample.class),
              mock(Timer.class),
              new AtomicBoolean(true));

      assertThat(rows).isZero();
    }

    @SneakyThrows
    @Test
    void rethrowsPsqlExceptionWhenNotCanceled() {
      when(props.getPageSize()).thenReturn(10);
      Connection conn = mock(Connection.class);
      Statement stmt = mock(Statement.class);
      when(ds.getConnection()).thenReturn(conn);
      when(conn.createStatement()).thenReturn(stmt);
      PSQLException boom = new PSQLException("boom", PSQLState.UNKNOWN_STATE);
      when(stmt.executeQuery(anyString())).thenThrow(boom);
      when(statementHolder.wasCanceled()).thenReturn(false);

      assertThatThrownBy(
              () ->
                  underTest.fetchChunk(
                      "select 1",
                      new PgFactExtractor(serial),
                      mock(Timer.Sample.class),
                      mock(Timer.class),
                      new AtomicBoolean(true)))
          .isSameAs(boom);
    }

    @SneakyThrows
    @Test
    void stopsProcessingWhenCanceledMidStream() {
      when(props.getPageSize()).thenReturn(10);
      Connection conn = mock(Connection.class);
      Statement stmt = mock(Statement.class);
      ResultSet rs = mock(ResultSet.class);
      when(ds.getConnection()).thenReturn(conn);
      when(conn.createStatement()).thenReturn(stmt);
      when(stmt.executeQuery(anyString())).thenReturn(rs);
      when(rs.next()).thenReturn(true);
      // canceled right away when iterating
      when(statementHolder.wasCanceled()).thenReturn(true);

      Timer timer = mock(Timer.class);
      Timer.Sample sample = mock(Timer.Sample.class);

      int rows =
          underTest.fetchChunk(
              "select 1", new PgFactExtractor(serial), sample, timer, new AtomicBoolean(false));

      assertThat(rows).isZero();
      verifyNoInteractions(pipeline);
    }
  }

  @Nested
  class WhenFetching {

    Connection conn;

    @SneakyThrows
    @BeforeEach
    void commonStubs() {
      when(req.specs()).thenReturn(Collections.emptyList());
      when(serial.get()).thenReturn(0L);
      when(metrics.timer(any(), anyBoolean())).thenReturn(mock(Timer.class));
      when(metrics.startSample()).thenReturn(mock(Timer.Sample.class));
      // the transaction manager opens a connection through the datasource
      conn = mock(Connection.class);
      lenient().when(conn.getAutoCommit()).thenReturn(true);
      lenient().when(ds.getConnection()).thenReturn(conn);
      // declaring/closing the cursor is exercised elsewhere
      lenient().doNothing().when(underTest).declareCursor(any(), anyString(), any(), any());
      lenient().doNothing().when(underTest).closeCursor(anyString());
    }

    @SneakyThrows
    @Test
    void returnsFalseImmediatelyWhenCanceledUpfront() {
      when(statementHolder.wasCanceled()).thenReturn(true);

      assertThat(underTest.fetch("catchup_x")).isFalse();

      verify(underTest, never()).fetchChunk(any(), any(), any(), any(), any());
    }

    @SneakyThrows
    @Test
    void returnsFalseForEmptyCursor() {
      when(statementHolder.wasCanceled()).thenReturn(false);
      when(props.getChunkSize()).thenReturn(10);
      doReturn(0).when(underTest).fetchChunk(any(), any(), any(), any(), any());

      assertThat(underTest.fetch("catchup_x")).isFalse();

      // only the single in-tx fetch happened, no further fetching
      verify(underTest, times(1)).fetchChunk(any(), any(), any(), any(), any());
      verify(statementHolder).clear();
    }

    @SneakyThrows
    @Test
    void returnsTrueForQuickCatchupWithoutHoldingCursor() {
      when(statementHolder.wasCanceled()).thenReturn(false);
      when(props.getChunkSize()).thenReturn(10);
      // fewer rows than chunk size but more than zero -> done within tx
      doReturn(3).when(underTest).fetchChunk(any(), any(), any(), any(), any());

      assertThat(underTest.fetch("catchup_x")).isTrue();

      verify(underTest, times(1)).fetchChunk(any(), any(), any(), any(), any());
    }

    @SneakyThrows
    @Test
    void firstRowIsFetchedInsideTransactionAndRemainderAfterCommit() {
      // chunk size 1: first (in-tx) fetch returns a full chunk -> cursor must be held,
      // remaining rows are fetched after the transaction committed.
      when(props.getChunkSize()).thenReturn(1);
      when(statementHolder.wasCanceled()).thenReturn(false);

      // first chunk (in-tx) full -> hold cursor; second chunk (post-commit) drains it
      doReturn(1, 0).when(underTest).fetchChunk(any(), any(), any(), any(), any());

      assertThat(underTest.fetch("catchup_x")).isTrue();

      // exactly two chunk fetches: one in-tx, one after commit
      verify(underTest, times(2)).fetchChunk(any(), any(), any(), any(), any());

      InOrder inOrder = inOrder(underTest, conn);
      // the first row is processed within the running transaction ...
      inOrder.verify(underTest).fetchChunk(any(), any(), any(), any(), any());
      // ... then the transaction is committed (CURSOR WITH HOLD persists the cursor) ...
      inOrder.verify(conn).commit();
      // ... and the remaining rows are fetched after the commit.
      inOrder.verify(underTest).fetchChunk(any(), any(), any(), any(), any());

      verify(statementHolder).clear();
    }

    @SneakyThrows
    @Test
    void stopsPostCommitFetchingWhenCanceled() {
      when(props.getChunkSize()).thenReturn(1);
      // not canceled for the first (in-tx) fetch, but canceled before the post-commit loop
      when(statementHolder.wasCanceled()).thenReturn(false, false, true);
      // first chunk is full -> cursor held and we enter the post-commit loop
      doReturn(1).when(underTest).fetchChunk(any(), any(), any(), any(), any());

      assertThat(underTest.fetch("catchup_x")).isTrue();

      // only the in-tx fetch happened; the post-commit loop bailed out due to cancellation
      verify(underTest, times(1)).fetchChunk(any(), any(), any(), any(), any());
    }

    @SneakyThrows
    @Test
    void unwrapsSqlExceptionRaisedWhilePostCommitFetching() {
      when(props.getChunkSize()).thenReturn(1);
      when(statementHolder.wasCanceled()).thenReturn(false);
      // first (in-tx) chunk full -> enter loop; the post-commit fetch then fails
      doReturn(1)
          .doThrow(new SQLException("fetch failed"))
          .when(underTest)
          .fetchChunk(any(), any(), any(), any(), any());

      assertThatThrownBy(() -> underTest.fetch("catchup_x"))
          .isInstanceOf(SQLException.class)
          .hasMessage("fetch failed");

      verify(statementHolder).clear();
    }

    @SneakyThrows
    @Test
    void unwrapsSqlExceptionRaisedWhileDeclaringCursor() {
      when(statementHolder.wasCanceled()).thenReturn(false);

      doThrow(new SQLException("declare failed"))
          .when(underTest)
          .declareCursor(any(), anyString(), any(), any());

      assertThatThrownBy(() -> underTest.fetch("catchup_x"))
          .isInstanceOf(SQLException.class)
          .hasMessage("declare failed");

      verify(statementHolder).clear();
    }
  }

  @SneakyThrows
  private static void mockSingleRow(ResultSet rs, long ser) {
    when(rs.next()).thenReturn(true, false);
    when(rs.getString(PgConstants.ALIAS_ID)).thenReturn(UUID.randomUUID().toString());
    when(rs.getString(PgConstants.ALIAS_AGGID)).thenReturn("[]");
    when(rs.getString(PgConstants.ALIAS_TYPE)).thenReturn("t");
    when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("ns");
    when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
    when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
    when(rs.getInt(PgConstants.COLUMN_VERSION)).thenReturn(1);
    when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(ser);
  }
}
