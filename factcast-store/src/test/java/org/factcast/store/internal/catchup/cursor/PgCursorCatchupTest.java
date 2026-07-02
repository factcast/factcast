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
package org.factcast.store.internal.catchup.cursor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.util.concurrent.atomic.*;
import lombok.SneakyThrows;
import org.factcast.core.subscription.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.PgMetrics;
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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PgCursorCatchupTest {

  @Mock(strictness = Mock.Strictness.LENIENT)
  StoreConfigurationProperties props;

  @Mock(strictness = Mock.Strictness.LENIENT)
  SubscriptionRequestTO req;

  @Mock CurrentStatementHolder statementHolder;
  @Mock ServerPipeline pipeline;

  @Mock(strictness = Mock.Strictness.LENIENT)
  PgMetrics metrics;

  @Mock Counter counter;

  @Mock Connection c;
  @Mock PreparedStatement p;
  @Mock ResultSet rs;
  @Mock AtomicLong serial;
  @Mock PgConnectionSupplier connectionSupplier;
  @Mock SingleConnectionDataSource ds;
  @Mock PlatformTransactionManager txMgr;
  @Mock PgCatchupFactory.Phase phase;

  @Spy @InjectMocks PgCursorCatchup underTest;
  @Mock Timer timer;
  @Mock Timer.Sample sample;

  @SneakyThrows
  @BeforeEach
  void setup() {
    lenient().when(ds.getConnection()).thenReturn(c);
    lenient().when(c.prepareStatement(anyString())).thenReturn(p);
    lenient().when(p.executeQuery()).thenReturn(rs);
    lenient().when(metrics.timer(any(), anyBoolean())).thenReturn(timer);
    lenient().when(metrics.startSample()).thenReturn(sample);
  }

  @Nested
  class WhenRunning {

    @SneakyThrows
    @Test
    void removesCurrentStatement() {
      when(req.debugInfo()).thenReturn("appName");
      var uut =
          spy(
              new PgCursorCatchup(
                  props,
                  metrics,
                  req,
                  pipeline,
                  serial,
                  statementHolder,
                  ds,
                  PgCatchupFactory.Phase.PHASE_1));
      uut.run();
      verify(statementHolder).clear();
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

      Assertions.assertDoesNotThrow(() -> cbh.processRow(rs));
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
