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
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.PreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
class PgHoldCursorCatchupTest {

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
                PgCatchupFactory.Phase.PHASE_1));
  }

  @Nested
  class WhenRunning {

    @SneakyThrows
    @Test
    void flushesClearsAndClosesCursor() {
      Connection connection = mock(Connection.class);
      doReturn(connection).when(ds).getConnection();
      doNothing().when(underTest).fetch(connection, "cursor_1");
      doReturn("cursor_1").when(underTest).createCursorName();
      doNothing().when(underTest).closeCursorQuietly(connection, "cursor_1");

      underTest.run();

      verify(underTest).closeCursorQuietly(connection, "cursor_1");
      verify(statementHolder).clear();
      verify(pipeline).process(argThat(Signal::indicatesFlush));
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
      when(req.specs()).thenReturn(java.util.Collections.emptyList());
      when(serial.get()).thenReturn(0L);
      when(metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, true)).thenReturn(timer);
      when(metrics.startSample()).thenReturn(sample);
    }

    @Test
    @SneakyThrows
    void declaresCursorCommitsAndFetchesUntilEmpty() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any());
      doReturn(2, 1, 0).when(underTest).fetchChunk(any(), anyString(), any(), any(), any(), any());

      underTest.fetch(connection, "cursor_1");

      verify(underTest)
          .declareCursor(
              same(connection),
              contains("DECLARE cursor_1 NO SCROLL CURSOR WITH HOLD FOR"),
              any(PreparedStatementSetter.class));
      verify(connection).commit();
      verify(connection).setAutoCommit(true);
      verify(connection).setAutoCommit(false);
      verify(underTest, times(3))
          .fetchChunk(
              same(connection), eq("FETCH FORWARD 47 FROM cursor_1"), any(), any(), any(), any());
    }

    @Test
    @SneakyThrows
    void returnsImmediatelyWhenCancelled() {
      doNothing().when(underTest).declareCursor(any(), anyString(), any());
      when(statementHolder.wasCanceled()).thenReturn(true);

      underTest.fetch(connection, "cursor_1");

      verify(underTest, never()).fetchChunk(any(), anyString(), any(), any(), any(), any());
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
      PreparedStatementSetter setter = p -> p.setLong(1, 1L);

      underTest.declareCursor(
          connection, "DECLARE foo NO SCROLL CURSOR WITH HOLD FOR SELECT 1", setter);

      verify(statementHolder).statement(declare, true);
      verify(declare).setLong(1, 1L);
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
  }

  @Test
  void testSqlHelpers() {
    when(props.getPageSize()).thenReturn(17);

    assertThat(underTest.createDeclareCursorWithHoldSql("cursor_1", "select 1"))
        .isEqualTo("DECLARE cursor_1 NO SCROLL CURSOR WITH HOLD FOR select 1");
    assertThat(underTest.createFetchSql("cursor_1")).isEqualTo("FETCH FORWARD 17 FROM cursor_1");
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
}
