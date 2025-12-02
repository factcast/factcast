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
package org.factcast.store.internal.catchup.fetching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.factcast.store.internal.catchup.fetching.PgFetchingCatchup.FIRST_ROW_FETCHING_THRESHOLD;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.subscription.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.pipeline.Signal;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@ExtendWith(MockitoExtension.class)
class PgFetchingCatchupTest {

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

  @Mock @NonNull Counter counter;

  @Mock @NonNull Connection c;
  @Mock @NonNull AtomicLong serial;
  @Mock @NonNull PgConnectionSupplier connectionSupplier;
  @Mock SingleConnectionDataSource ds;
  @Mock PgCatchupFactory.Phase phase;

  @Spy @InjectMocks PgFetchingCatchup underTest;

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
              new PgFetchingCatchup(
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

      // TODO #4124
      // move this to where we create the connection
      // verify(connectionSupplier)
      //    .getPooledAsSingleDataSource(
      //        ConnectionModifier.withAutoCommitDisabled(),
      //        ConnectionModifier.withApplicationName(req.debugInfo()));
    }

    @SneakyThrows
    @Test
    void removesCurrentStatement() {
      when(req.debugInfo()).thenReturn("appName");
      var uut =
          spy(
              new PgFetchingCatchup(
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
  class WhenFetching {
    @Mock @NonNull JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
      when(props.getPageSize()).thenReturn(47);
      when(metrics.counter(StoreMetrics.EVENT.FACTS_SENT)).thenReturn(counter);
    }

    @Test
    void setsCorrectFetchSize() {
      doNothing()
          .when(jdbc)
          .query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
      underTest.fetch(jdbc);
      verify(jdbc).setFetchSize(props.getPageSize());
    }

    @Test
    void usesTimedRowCallbackHandlerFromScratch() {
      doNothing()
          .when(jdbc)
          .query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
      // from scratch
      when(serial.get()).thenReturn(0L);

      underTest.fetch(jdbc);

      verify(metrics, times(1)).timer(StoreMetrics.OP.RESULT_STREAM_START, true);
      verify(underTest, times(1)).createTimedRowCallbackHandler(any(), any());
    }

    @Test
    void usesTimedRowCallbackHandlerFromSerial() {
      doNothing()
          .when(jdbc)
          .query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
      // from serial
      when(serial.get()).thenReturn(42L);

      underTest.fetch(jdbc);

      verify(metrics, times(1)).timer(StoreMetrics.OP.RESULT_STREAM_START, false);
      verify(underTest, times(1)).createTimedRowCallbackHandler(any(), any());
    }
  }

  @SuppressWarnings("resource")
  @Nested
  class WhenCreatingRowCallbackHandler {
    @Mock PgFactExtractor extractor;

    @SneakyThrows
    @Test
    void passesFact() {
      final var cbh = underTest.createRowCallbackHandler(extractor);
      ResultSet rs = mock(ResultSet.class);
      PgFact testFact = Mockito.mock(PgFact.class);
      when(extractor.mapRow(rs, 0)).thenReturn(testFact);
      cbh.processRow(rs);

      verify(pipeline).process(Signal.of(testFact));
    }

    @SneakyThrows
    @Test
    void passesFactEscalatesException() {
      final var cbh = underTest.createRowCallbackHandler(extractor);
      ResultSet rs = mock(ResultSet.class);
      PgFact testFact = Mockito.mock(PgFact.class);
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      doThrow(TransformationException.class).when(pipeline).process(Signal.of(testFact));

      assertThatThrownBy(() -> cbh.processRow(rs)).isInstanceOf(TransformationException.class);
    }

    @Test
    @SneakyThrows
    void swallowsExceptionAfterCancel() {
      final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
      ResultSet rs = mock(ResultSet.class);
      when(statementHolder.wasCanceled()).thenReturn(false, true);

      // until
      PSQLException mockException = mock(PSQLException.class);
      when(rs.getString(anyString())).thenThrow(mockException);

      org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> cbh.processRow(rs));
    }

    @Test
    @SneakyThrows
    void returnsIfCancelled() {
      final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
      ResultSet rs = mock(ResultSet.class);
      when(statementHolder.wasCanceled()).thenReturn(true);

      Assertions.assertDoesNotThrow(() -> cbh.processRow(rs));
    }

    @Test
    @SneakyThrows
    void throwsWhenNotCanceled() {
      final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
      ResultSet rs = mock(ResultSet.class);
      // it should appear open,
      when(rs.isClosed()).thenReturn(false);
      // until
      PSQLException mockException =
          mock(PSQLException.class, withSettings().strictness(Strictness.LENIENT));
      when(rs.getString(anyString())).thenThrow(mockException);

      assertThatThrownBy(() -> cbh.processRow(rs)).isInstanceOf(SQLException.class);
    }

    @Test
    @SneakyThrows
    void throwsWhenCanceledButUnexpectedException() {
      final var cbh = underTest.createRowCallbackHandler(extractor);
      ResultSet rs = mock(ResultSet.class);
      // it should appear open,
      when(rs.isClosed()).thenReturn(false);
      // until
      when(extractor.mapRow(any(), anyInt())).thenThrow(RuntimeException.class);

      assertThatThrownBy(() -> cbh.processRow(rs)).isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  class WhenCreatingTimedRowCallbackHandler {
    @Mock PgFactExtractor extractor;
    @Mock RowCallbackHandler wrappedCbh;
    @Mock Timer timer;
    @Mock Timer.Sample timerSample;

    @SneakyThrows
    @BeforeEach
    void setup() {
      doReturn(wrappedCbh).when(underTest).createRowCallbackHandler(extractor);
      doNothing().when(wrappedCbh).processRow(any());
      doReturn(timerSample).when(metrics).startSample();
    }

    @SneakyThrows
    @Test
    void stopsTimerOnceAndDelegates() {
      final var tcbh = underTest.createTimedRowCallbackHandler(extractor, timer);
      ResultSet rs1 = mock(ResultSet.class);
      ResultSet rs2 = mock(ResultSet.class);

      tcbh.processRow(rs1);
      tcbh.processRow(rs2);

      verify(wrappedCbh).processRow(rs1);
      verify(wrappedCbh).processRow(rs2);
      verify(timerSample, times(1)).stop(timer);
    }

    @SneakyThrows
    @Test
    void logsIfAboveThreshold() {
      try (LogCaptor logCaptor = LogCaptor.forClass(PgFetchingCatchup.class)) {
        final var tcbh = underTest.createTimedRowCallbackHandler(extractor, timer);
        final var elapsed = FIRST_ROW_FETCHING_THRESHOLD.plusSeconds(5);
        ResultSet rs = mock(ResultSet.class);
        when(timerSample.stop(timer)).thenReturn(elapsed.toNanos());

        tcbh.processRow(rs);

        assertThat(logCaptor.getInfoLogs())
            .first()
            .asString()
            .contains("took " + elapsed.toSeconds() + "s to stream the first result set");
      }
    }
  }
}
