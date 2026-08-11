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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.sql.*;
import java.util.concurrent.atomic.*;
import lombok.SneakyThrows;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.factcast.core.subscription.*;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.*;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class PgCursorCatchupTest {

  @Mock(strictness = Mock.Strictness.LENIENT)
  StoreConfigurationProperties props;

  @Mock(strictness = Mock.Strictness.LENIENT)
  SubscriptionRequestTO req;

  @Mock ServerPipeline pipeline;

  @Mock(strictness = Mock.Strictness.LENIENT)
  PgMetrics metrics;

  @Mock Counter counter;

  @Mock Connection c;
  @Mock org.apache.tomcat.jdbc.pool.PooledConnection pooled;
  @Mock PgConnection pg;

  @Mock PreparedStatement p;
  @Mock ResultSet rs;
  @Mock AtomicLong serial;
  @Mock PgConnectionSupplier connectionSupplier;
  @Mock SingleConnectionDataSource ds;
  @Mock PlatformTransactionManager txMgr;
  @Mock PgFactExtractor extractor;

  PgCursorCatchup underTest;
  @Mock Timer timer;
  @Mock Timer.Sample sample;

  @SneakyThrows
  @BeforeEach
  void setup() {
    lenient().when(ds.getConnection()).thenReturn(c);
    lenient().when(c.prepareStatement(anyString())).thenReturn(p);
    lenient().when(c.prepareStatement(anyString())).thenReturn(p);
    lenient().when(p.executeQuery()).thenReturn(rs);
    lenient().when(metrics.timer(any(), anyBoolean())).thenReturn(timer);
    lenient().when(metrics.startSample()).thenReturn(sample);
    lenient().when(c.unwrap(PooledConnection.class)).thenReturn(pooled);
    lenient().when(c.unwrap(PgConnection.class)).thenReturn(pg);
    lenient().when(pooled.isDiscarded()).thenReturn(false);

    underTest =
        new PgCursorCatchup(
            props, metrics, req, pipeline, serial, ds, PgCatchupFactory.Phase.PHASE_1);
  }

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

    doReturn(true).when(pooled).isDiscarded();

    final var cbh = underTest.createRowCallbackHandler(extractor);
    ResultSet rs = mock(ResultSet.class);

    // until
    SQLException mockException = mock(SQLException.class);
    lenient().when(rs.getString(anyString())).thenThrow(mockException);

    assertDoesNotThrow(() -> cbh.processRow(rs));
  }

  @Test
  @SneakyThrows
  void returnsIfCancelled() {
    final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
    ResultSet rs = mock(ResultSet.class);
    when(pooled.isDiscarded()).thenReturn(true);

    assertDoesNotThrow(() -> cbh.processRow(rs));
  }

  @Test
  @SneakyThrows
  void throwsWhenNotCanceled() {
    final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
    ResultSet rs = mock(ResultSet.class);
    // it should appear open,
    when(rs.isClosed()).thenReturn(false);
    // until
    SQLException mockException =
        mock(SQLException.class, withSettings().strictness(Strictness.LENIENT));
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
