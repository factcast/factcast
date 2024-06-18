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
package org.factcast.store.internal;

import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;

class PgFactStreamTelemetryTest {
  @Mock JdbcTemplate jdbcTemplate;
  @Mock EventBus eventBus;
  @Mock PgFactIdToSerialMapper idToSerMapper;
  @Mock SubscriptionImpl subscription;
  @Mock PgLatestSerialFetcher fetcher;
  @Mock PgCatchupFactory pgCatchupFactory;
  @Mock FastForwardTarget ffwdTarget;
  @Mock FactTransformerService transformationService;
  @Mock Blacklist blacklist;
  @Mock PgMetrics metrics;
  @Mock JSEngineFactory ef;
  @Mock PgStoreTelemetry telemetry;
  @Mock DataSource dataSource;

  @InjectMocks PgFactStream uut;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void postsTelemetryOnCatchup() {
    var req = mock(SubscriptionRequestTO.class);
    when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
    when(pgCatchupFactory.create(eq(req), eq(subscription), any(), any(), any()))
        .thenReturn(mock(PgCatchup.class));

    uut.connect(req);

    InOrder inOrder = inOrder(telemetry);
    inOrder.verify(telemetry).onConnect(req);
    inOrder.verify(telemetry).onCatchup(req);
    inOrder.verify(telemetry).onComplete(req);
  }

  @SneakyThrows
  @Test
  void postsTelemetryOnFollow() {
    var req = mock(SubscriptionRequestTO.class);
    when(req.continuous()).thenReturn(true);
    when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
    when(dataSource.getConnection()).thenReturn(mock(java.sql.Connection.class));
    when(pgCatchupFactory.create(eq(req), eq(subscription), any(), any(), any()))
        .thenReturn(mock(PgCatchup.class));

    uut.connect(req);

    InOrder inOrder = inOrder(telemetry);
    inOrder.verify(telemetry).onConnect(req);
    inOrder.verify(telemetry).onCatchup(req);
    inOrder.verify(telemetry).onFollow(req);
  }

  @SneakyThrows
  @Test
  void postsTelemetryOnClose() {
    var req = mock(SubscriptionRequestTO.class);
    when(req.continuous()).thenReturn(true);
    when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
    when(dataSource.getConnection()).thenReturn(mock(java.sql.Connection.class));
    when(pgCatchupFactory.create(eq(req), eq(subscription), any(), any(), any()))
        .thenReturn(mock(PgCatchup.class));
    uut.connect(req);

    uut.close();

    verify(telemetry).onClose(req);
  }
}
