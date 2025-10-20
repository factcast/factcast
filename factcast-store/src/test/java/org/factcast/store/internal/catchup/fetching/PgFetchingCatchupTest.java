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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import java.sql.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.SneakyThrows;
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

  @InjectMocks PgFetchingCatchup underTest;

  @SneakyThrows
  @BeforeEach
  void setup() {}

  @Nested
  class WhenRunning {

    @SneakyThrows
    @Test
    void connectionHandling() {

      when(req.debugInfo()).thenReturn("appName");
      when(connectionSupplier.getPooledAsSingleDataSource(any(ConnectionModifier[].class)))
          .thenReturn(ds);
      var uut =
          spy(
              new PgFetchingCatchup(
                  connectionSupplier,
                  props,
                  req,
                  pipeline,
                  serial,
                  statementHolder,
                  PgCatchupFactory.Phase.PHASE_1));
      doNothing().when(uut).fetch(any());
      uut.run();

      verify(connectionSupplier)
          .getPooledAsSingleDataSource(
              ConnectionModifier.withAutoCommitDisabled(),
              ConnectionModifier.withApplicationName(req.debugInfo()));
    }

    @SneakyThrows
    @Test
    void removesCurrentStatement() {
      when(req.debugInfo()).thenReturn("appName");
      when(connectionSupplier.getPooledAsSingleDataSource(any(ConnectionModifier[].class)))
          .thenReturn(ds);
      var uut =
          spy(
              new PgFetchingCatchup(
                  connectionSupplier,
                  props,
                  req,
                  pipeline,
                  serial,
                  statementHolder,
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
}
