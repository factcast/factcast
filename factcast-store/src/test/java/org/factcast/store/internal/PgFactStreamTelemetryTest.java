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
import lombok.SneakyThrows;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.*;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
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
  @Mock ServerPipeline serverPipeline;
  @Mock JSEngineFactory ef;
  @Mock PgStoreTelemetry telemetry;
  @Mock SubscriptionRequestTO req;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  PgConnectionSupplier connectionSupplier;

  @InjectMocks PgFactStream uut;

  @Test
  void postsTelemetryOnCatchup() {
    when(req.debugInfo()).thenReturn("test");
    when(pgCatchupFactory.create(eq(req), eq(serverPipeline), any(), any(), any()))
        .thenReturn(mock(PgCatchup.class));
    when(ffwdTarget.highWaterMark()).thenReturn(HighWaterMark.empty());
    uut.connect();

    InOrder inOrder = inOrder(telemetry);
    inOrder.verify(telemetry).onConnect(req);
    inOrder.verify(telemetry).onCatchup(req);
    inOrder.verify(telemetry).onComplete(req);
  }

  @SneakyThrows
  @Test
  void postsTelemetryOnFollow() {
    when(req.continuous()).thenReturn(true);
    when(req.debugInfo()).thenReturn("test");
    when(pgCatchupFactory.create(eq(req), eq(serverPipeline), any(), any(), any()))
        .thenReturn(mock(PgCatchup.class));
    when(ffwdTarget.highWaterMark()).thenReturn(HighWaterMark.empty());

    uut.connect();

    InOrder inOrder = inOrder(telemetry);
    inOrder.verify(telemetry).onConnect(req);
    inOrder.verify(telemetry).onCatchup(req);
    inOrder.verify(telemetry).onFollow(req);
  }

  @SneakyThrows
  @Test
  void postsTelemetryOnClose() {
    when(req.continuous()).thenReturn(true);
    when(req.debugInfo()).thenReturn("test");
    when(pgCatchupFactory.create(eq(req), eq(serverPipeline), any(), any(), any()))
        .thenReturn(mock(PgCatchup.class));
    when(ffwdTarget.highWaterMark()).thenReturn(HighWaterMark.empty());

    uut.connect();

    uut.close();

    verify(telemetry).onClose(req);
  }
}
