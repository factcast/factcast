/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal.catchup.chunked;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.subscription.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@ExtendWith(MockitoExtension.class)
class PgChunkedCatchupTest {

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

  @Mock @NonNull Connection c;
  @Mock @NonNull AtomicLong serial;
  @Mock SingleConnectionDataSource ds;
  @Mock PgCatchupFactory.Phase phase;

  @Spy @InjectMocks PgChunkedCatchup underTest;

  @BeforeEach
  void setup() {}

  @Nested
  class WhenRunning {

    @SneakyThrows
    @Test
    void connectionHandling() {
      when(req.debugInfo()).thenReturn("appName");
      var uut =
          spy(
              new PgChunkedCatchup(
                  props,
                  metrics,
                  req,
                  pipeline,
                  serial,
                  statementHolder,
                  ds,
                  PgCatchupFactory.Phase.PHASE_1));
      doNothing().when(uut).fetch(any());
      uut.run();
    }

    @SneakyThrows
    @Test
    void flushesOnFinally() {
      when(req.debugInfo()).thenReturn("appName");
      var uut =
          spy(
              new PgChunkedCatchup(
                  props,
                  metrics,
                  req,
                  pipeline,
                  serial,
                  statementHolder,
                  ds,
                  PgCatchupFactory.Phase.PHASE_1));
      doNothing().when(uut).fetch(any());

      uut.run();

      ArgumentCaptor<Signal> sigCap = ArgumentCaptor.forClass(Signal.class);
      verify(pipeline).process(sigCap.capture());
      assertThat(sigCap.getValue().indicatesFlush()).isTrue();
    }

    @SneakyThrows
    @Test
    void removesCurrentStatement() {
      when(req.debugInfo()).thenReturn("appName");
      var uut =
          spy(
              new PgChunkedCatchup(
                  props,
                  metrics,
                  req,
                  pipeline,
                  serial,
                  statementHolder,
                  ds,
                  PgCatchupFactory.Phase.PHASE_1));
      doNothing().when(uut).fetch(any());
      uut.run();

      verify(statementHolder).clear();
    }
  }

  @Nested
  class WhenUsingHelpers {

    @Test
    void createTempTableExecutesExpectedSQL() throws Exception {
      JdbcTemplate jdbc = mock(JdbcTemplate.class);
      var m =
          PgChunkedCatchup.class.getDeclaredMethod(
              "createTempTable", JdbcTemplate.class, String.class);
      m.setAccessible(true);

      m.invoke(underTest, jdbc, "tmp_123");

      verify(jdbc).execute("create temp table tmp_123 (ser bigint primary key)");
    }

    @Test
    void createSingleDSClosesUnderlyingConnectionOnDestroy() throws Exception {
      DataSource dsMock = mock(DataSource.class);
      Connection conn = mock(Connection.class);
      when(dsMock.getConnection()).thenReturn(conn);

      var m = PgChunkedCatchup.class.getDeclaredMethod("createSingleDS", DataSource.class);
      m.setAccessible(true);

      SingleConnectionDataSource scds = (SingleConnectionDataSource) m.invoke(underTest, dsMock);

      scds.destroy();

      verify(conn, atLeastOnce()).close();
    }

    @Test
    void logIfAboveThresholdEmitsInfo() throws Exception {
      try (LogCaptor logCaptor = LogCaptor.forClass(PgChunkedCatchup.class)) {
        var elapsed = PgChunkedCatchup.FIRST_ROW_FETCHING_THRESHOLD.plusSeconds(2);
        underTest.logIfAboveThreshold(elapsed);

        assertThat(logCaptor.getInfoLogs()).isNotEmpty();
        assertThat(logCaptor.getInfoLogs().get(0))
            .contains("took " + elapsed.toSeconds() + "s to find all the serials matching");
      }
    }

    @Test
    void prepareChunkQuery() {
      when(props.getPageSize()).thenReturn(123);
      String tableName = "tmp_table_name";
      String query = underTest.prepareChunkQuery(tableName);

      assertThat(query)
          .contains(PgConstants.PROJECTION_FACT)
          .contains("delete from " + tableName)
          .contains("select ser from " + tableName)
          .contains("limit 123");
    }

    @Test
    void prepareTemporaryTableInvokesJdbcUpdateAndReturnsMatches() {
      JdbcTemplate jdbc = mock(JdbcTemplate.class);
      // from scratch -> true
      when(serial.get()).thenReturn(0L);
      when(req.specs()).thenReturn(java.util.Collections.emptyList());

      io.micrometer.core.instrument.Timer timer = mock(io.micrometer.core.instrument.Timer.class);
      io.micrometer.core.instrument.Timer.Sample sample =
          mock(io.micrometer.core.instrument.Timer.Sample.class);

      when(metrics.timer(StoreMetrics.OP.RESULT_STREAM_START, true)).thenReturn(timer);
      when(metrics.startSample()).thenReturn(sample);

      long nanos = PgChunkedCatchup.FIRST_ROW_FETCHING_THRESHOLD.plusSeconds(2).toNanos();
      when(sample.stop(timer)).thenReturn(nanos);

      when(jdbc.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(7);

      int matches = underTest.prepareTemporaryTable(jdbc, "tmp_table");

      assertThat(matches).isEqualTo(7);
      verify(jdbc).execute("create temp table tmp_table (ser bigint primary key)");
      verify(metrics).timer(StoreMetrics.OP.RESULT_STREAM_START, true);
      verify(metrics).startSample();
      verify(sample).stop(timer);
      verify(jdbc).update(anyString(), any(PreparedStatementSetter.class));
    }
  }

  @Nested
  class WhenFetching {

    @Test
    @SneakyThrows
    void fetchDropsTempTableAndHonorsCancellation() {
      // arrange a SingleConnectionDataSource that provides a JDBC Connection/Statement
      SingleConnectionDataSource scds = mock(SingleConnectionDataSource.class);
      Connection conn = mock(Connection.class);
      Statement stmt = mock(Statement.class);
      when(scds.getConnection()).thenReturn(conn);
      when(conn.createStatement()).thenReturn(stmt);
      when(stmt.execute(anyString())).thenReturn(true);

      // spy the UUT to intercept helpers used inside fetch
      doReturn(scds).when(underTest).createSingleDS(ds);
      // ensure there are matching serials, so the while-loop is reached
      doReturn(1).when(underTest).prepareTemporaryTable(any(), anyString());
      // cancel immediately so no query is executed and the method returns early
      when(statementHolder.wasCanceled()).thenReturn(true);

      // act
      underTest.fetch(ds);

      // assert: temp table is dropped in finally and no facts are processed
      verify(stmt).execute(argThat(sql -> sql.toLowerCase().startsWith("drop table catchup_")));
      verifyNoInteractions(pipeline);
    }

    @Test
    @SneakyThrows
    void fetch() {
      // Arrange a SingleConnectionDataSource backed by mocked JDBC artifacts
      SingleConnectionDataSource scds = mock(SingleConnectionDataSource.class);
      Connection conn = mock(Connection.class);
      Statement stmt = mock(Statement.class);
      ResultSet rs1 = mock(ResultSet.class);
      ResultSet rs2 = mock(ResultSet.class);
      ResultSet rs3 = mock(ResultSet.class);
      ResultSet rs4 = mock(ResultSet.class);

      when(scds.getConnection()).thenReturn(conn);
      when(conn.createStatement()).thenReturn(stmt);
      // drop table succeeds
      when(stmt.execute(anyString())).thenReturn(true);
      // query returns 1 row, then 1 row, then 1 row, then 0 rows
      when(stmt.executeQuery(anyString())).thenReturn(rs1, rs2, rs3, rs4);

      // Configure the rows for PgFactExtractor/PgFact.from
      // rs1: one row
      when(rs1.next()).thenReturn(true, false);
      when(rs1.getString(PgConstants.ALIAS_ID)).thenReturn(java.util.UUID.randomUUID().toString());
      when(rs1.getString(PgConstants.ALIAS_AGGID)).thenReturn("[]");
      when(rs1.getString(PgConstants.ALIAS_TYPE)).thenReturn("t");
      when(rs1.getString(PgConstants.ALIAS_NS)).thenReturn("ns");
      when(rs1.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs1.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs1.getInt(PgConstants.COLUMN_VERSION)).thenReturn(1);
      when(rs1.getLong(PgConstants.COLUMN_SER)).thenReturn(1L);

      // rs2: one row
      when(rs2.next()).thenReturn(true, false);
      when(rs2.getString(PgConstants.ALIAS_ID)).thenReturn(java.util.UUID.randomUUID().toString());
      when(rs2.getString(PgConstants.ALIAS_AGGID)).thenReturn("[]");
      when(rs2.getString(PgConstants.ALIAS_TYPE)).thenReturn("t");
      when(rs2.getString(PgConstants.ALIAS_NS)).thenReturn("ns");
      when(rs2.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs2.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs2.getInt(PgConstants.COLUMN_VERSION)).thenReturn(1);
      when(rs2.getLong(PgConstants.COLUMN_SER)).thenReturn(2L);

      // rs3: one row
      when(rs3.next()).thenReturn(true, false);
      when(rs3.getString(PgConstants.ALIAS_ID)).thenReturn(java.util.UUID.randomUUID().toString());
      when(rs3.getString(PgConstants.ALIAS_AGGID)).thenReturn("[]");
      when(rs3.getString(PgConstants.ALIAS_TYPE)).thenReturn("t");
      when(rs3.getString(PgConstants.ALIAS_NS)).thenReturn("ns");
      when(rs3.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs3.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs3.getInt(PgConstants.COLUMN_VERSION)).thenReturn(1);
      when(rs3.getLong(PgConstants.COLUMN_SER)).thenReturn(3L);

      // rs4: zero rows
      when(rs4.next()).thenReturn(false);

      // Spy the UUT to intercept helpers used inside fetch
      doReturn(scds).when(underTest).createSingleDS(ds);
      // ensure there are matching serials, so the while-loop is reached
      doReturn(1).when(underTest).prepareTemporaryTable(any(), anyString());
      // do not cancel during the loop
      when(statementHolder.wasCanceled()).thenReturn(false);

      // Act
      underTest.fetch(ds);

      // Assert: query loop ran 4 iterations (3 with rows, 1 with 0 rows to finish)
      verify(stmt, atLeast(4)).executeQuery(anyString());
      // And the pipeline processed at least three facts (one per iteration)
      verify(pipeline, atLeast(3)).process(any());
    }
  }
}
