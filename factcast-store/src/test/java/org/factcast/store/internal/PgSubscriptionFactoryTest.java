/*
 * Copyright © 2017-2022 factcast.org
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.factcast.core.subscription.MissingTransformationInformationException;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.HighWaterMarkFetcher;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.pipeline.ServerPipelineFactory;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgSubscriptionFactoryTest {

  @Mock private EventBus eventBus;
  @Mock private PgFactIdToSerialMapper idToSerialMapper;
  @Mock private PgCatchupFactory catchupFactory;

  @Mock private StoreConfigurationProperties props;

  @Mock private HighWaterMarkFetcher target;
  @Mock private PgMetrics metrics;
  @Mock private PgStoreTelemetry telemetry;

  @Mock private JSEngineFactory engineFactory;
  @Mock private ServerPipelineFactory pipelineFactory;
  @Mock private PgConnectionSupplier connectionSupplier;

  @Spy private ExecutorService executorService = Executors.newSingleThreadExecutor();
  private PgSubscriptionFactory underTest;

  @BeforeEach
  void setUp() {
    when(props.getSizeOfThreadPoolForSubscriptions()).thenReturn(1);
    when(metrics.monitor(any(), anyString())).thenReturn(executorService);
    underTest =
        new PgSubscriptionFactory(
            connectionSupplier,
            eventBus,
            idToSerialMapper,
            props,
            catchupFactory,
            target,
            pipelineFactory,
            engineFactory,
            metrics,
            telemetry);
  }

  @Nested
  class WhenSubscribing {
    @Mock private SubscriptionRequestTO req;
    @Mock private FactObserver observer;

    @Test
    void testSubscribe_happyCase() {
      final var runnable = mock(Runnable.class);
      final var spyUut = spy(underTest);
      doReturn(runnable).when(spyUut).connect(any(), any());

      spyUut.subscribe(req, observer);
        verify(spyUut).connect(any(), any());
      verify(runnable, timeout(100)).run();

    }
  }

  @Nested
  class WhenConnecting {
    @Mock private SubscriptionRequestTO req;
    @Mock private SubscriptionImpl subscription;
    @Mock private PgFactStream pgsub;

    @BeforeEach
    void setUp() {
      lenient().when(pgsub.request()).thenReturn(req);
    }

    @Test
    void testConnect_happyCase() {
      underTest.connect(subscription, pgsub).run();
      verify(pgsub).connect();
    }

    @Test
    void testConnect_transformationException() {
      var e = new TransformationException("foo");

      doThrow(e).when(pgsub).connect();

      underTest.connect(subscription, pgsub).run();

      verify(pgsub).connect();
      verify(subscription).notifyError(e);
    }

    @Test
    void testConnect_someException() {
      var e = new IllegalArgumentException("foo");

      doThrow(e).when(pgsub).connect();

      underTest.connect(subscription, pgsub).run();

      verify(pgsub).connect();
      verify(subscription).notifyError(e);
    }

    @Test
    void warnsForMissingTransformations() {
      underTest = spy(underTest);
      doThrow(MissingTransformationInformationException.class).when(pgsub).connect();

      underTest.connect(subscription, pgsub).run();

      verify(underTest)
          .warnAndNotify(
              same(subscription),
              same(req),
              eq("missing transformation"),
              any(MissingTransformationInformationException.class));
      verify(subscription).notifyError(any(MissingTransformationInformationException.class));
    }

    @Test
    void errsForTransformationErrors() {
      underTest = spy(underTest);
      doThrow(TransformationException.class).when(pgsub).connect();

      underTest.connect(subscription, pgsub).run();

      verify(underTest)
          .errorAndNotify(
              same(subscription),
              same(req),
              eq("failing transformation"),
              any(TransformationException.class));
      verify(subscription).notifyError(any(TransformationException.class));
    }

    @Test
    void warnsForRuntimeExceptions() {
      underTest = spy(underTest);
      doThrow(RuntimeException.class).when(pgsub).connect();

      underTest.connect(subscription, pgsub).run();

      verify(underTest)
          .warnAndNotify(same(subscription), same(req), eq("runtime"), any(RuntimeException.class));
      verify(subscription).notifyError(any(Exception.class));
    }
  }
}
