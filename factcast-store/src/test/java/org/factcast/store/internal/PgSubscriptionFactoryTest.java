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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Disabled // TODO
class PgSubscriptionFactoryTest {
  //
  //  @Mock private JdbcTemplate jdbcTemplate;
  //  @Mock private EventBus eventBus;
  //  @Mock private PgFactIdToSerialMapper idToSerialMapper;
  //  @Mock private PgLatestSerialFetcher fetcher;
  //  @Mock private PgCatchupFactory catchupFactory;
  //
  //  @Mock private StoreConfigurationProperties props;
  //
  //  @Mock private Blacklist blacklist;
  //  @Mock private FastForwardTarget target;
  //  @Mock private FactTransformerService transformerService;
  //  @Mock private PgMetrics metrics;
  //
  //  @Mock private JSEngineFactory engineFactory;
  //
  //  @Spy private ExecutorService executorService = Executors.newSingleThreadExecutor();
  //  private PgSubscriptionFactory underTest;
  //
  //  @BeforeEach
  //  void setUp() {
  //    when(props.getSizeOfThreadPoolForSubscriptions()).thenReturn(1);
  //    when(metrics.monitor(any(), anyString())).thenReturn(executorService);
  //    underTest =
  //        new PgSubscriptionFactory(
  //            jdbcTemplate,
  //            eventBus,
  //            idToSerialMapper,
  //            fetcher,
  //            props,
  //            catchupFactory,
  //            target,
  //            metrics,
  //            blacklist,
  //            transformerService,
  //            engineFactory);
  //  }
  //
  //  @Nested
  //  class WhenSubscribing {
  //    @Mock private SubscriptionRequestTO req;
  //    @Mock private FactObserver observer;
  //
  //    @Test
  //    void testSubscribe_happyCase() {
  //      final var runnable = mock(Runnable.class);
  //      final var spyUut = spy(underTest);
  //      doReturn(runnable).when(spyUut).connect(any(), any(), any());
  //
  //      try (var cf = Mockito.mockStatic(CompletableFuture.class)) {
  //        spyUut.subscribe(req, observer);
  //        cf.verify(() -> CompletableFuture.runAsync(runnable, executorService));
  //      }
  //    }
  //  }
  //
  //  @Nested
  //  class WhenConnecting {
  //    @Mock private SubscriptionRequestTO req;
  //    @Mock private SubscriptionImpl subscription;
  //    @Mock private PgFactStream pgsub;
  //
  //    @Test
  //    void testConnect_happyCase() {
  //      underTest.connect(req, subscription, pgsub).run();
  //
  //      verify(pgsub).connect(req);
  //    }
  //
  //    @Test
  //    void testConnect_transformationException() {
  //      var e = new TransformationException("foo");
  //
  //      doThrow(e).when(pgsub).connect(req);
  //
  //      underTest.connect(req, subscription, pgsub).run();
  //
  //      verify(pgsub).connect(req);
  //      verify(subscription).notifyError(e);
  //    }
  //
  //    @Test
  //    void testConnect_someException() {
  //      var e = new IllegalArgumentException("foo");
  //
  //      doThrow(e).when(pgsub).connect(req);
  //
  //      underTest.connect(req, subscription, pgsub).run();
  //
  //      verify(pgsub).connect(req);
  //      verify(subscription).notifyError(e);
  //    }
  //
  //    @Test
  //    void warnsForMissingTransformations() {
  //      underTest = spy(underTest);
  //      doThrow(MissingTransformationInformationException.class).when(pgsub).connect(any());
  //
  //      underTest.connect(req, subscription, pgsub).run();
  //
  //      verify(underTest)
  //          .warnAndNotify(
  //              same(subscription),
  //              same(req),
  //              eq("missing transformation"),
  //              any(MissingTransformationInformationException.class));
  //      verify(subscription).notifyError(any(MissingTransformationInformationException.class));
  //    }
  //
  //    @Test
  //    void errsForTransformationErrors() {
  //      underTest = spy(underTest);
  //      doThrow(TransformationException.class).when(pgsub).connect(any());
  //
  //      underTest.connect(req, subscription, pgsub).run();
  //
  //      verify(underTest)
  //          .errorAndNotify(
  //              same(subscription),
  //              same(req),
  //              eq("failing transformation"),
  //              any(TransformationException.class));
  //      verify(subscription).notifyError(any(TransformationException.class));
  //    }
  //
  //    @Test
  //    void warnsForRuntimeExceptions() {
  //      underTest = spy(underTest);
  //      doThrow(RuntimeException.class).when(pgsub).connect(any());
  //
  //      underTest.connect(req, subscription, pgsub).run();
  //
  //      verify(underTest)
  //          .warnAndNotify(same(subscription), same(req), eq("runtime"),
  // any(RuntimeException.class));
  //      verify(subscription).notifyError(any(Exception.class));
  //    }
  //  }
}
