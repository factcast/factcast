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
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.MissingTransformationInformationException;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PgSubscriptionFactoryTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private EventBus eventBus;
  @Mock private PgFactIdToSerialMapper idToSerialMapper;
  @Mock private PgLatestSerialFetcher fetcher;
  @Mock private PgCatchupFactory catchupFactory;
  @Mock private FastForwardTarget target;
  @Mock private FactTransformerService transformerService;
  @Mock private PgMetrics metrics;
  @InjectMocks private PgSubscriptionFactory underTest;

  @Nested
  class WhenSubscribing {
    @Mock private SubscriptionRequestTO req;
    @Mock private FactObserver observer;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenConnecting {
    @Mock private SubscriptionRequestTO req;
    @Mock private SubscriptionImpl subscription;
    @Mock private PgFactStream pgsub;

    @Test
    void testConnect_happyCase() {
      underTest.connect(req, subscription, pgsub).run();

      verify(pgsub).connect(req);
    }

    @Test
    void testConnect_transformationException() {
      var e = new TransformationException("foo");

      doThrow(e).when(pgsub).connect(req);

      underTest.connect(req, subscription, pgsub).run();

      verify(pgsub).connect(req);
      verify(subscription).notifyError(e);
    }

    @Test
    void testConnect_someException() {
      var e = new IllegalArgumentException("foo");

      doThrow(e).when(pgsub).connect(req);

      underTest.connect(req, subscription, pgsub).run();

      verify(pgsub).connect(req);
      verify(subscription).notifyError(e);
    }

    @Test
    void warnsForMissingTransformations() {
      underTest = spy(underTest);
      doThrow(MissingTransformationInformationException.class).when(pgsub).connect(any());

      underTest.connect(req, subscription, pgsub).run();

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
      doThrow(TransformationException.class).when(pgsub).connect(any());

      underTest.connect(req, subscription, pgsub).run();

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
      doThrow(RuntimeException.class).when(pgsub).connect(any());

      underTest.connect(req, subscription, pgsub).run();

      verify(underTest)
          .warnAndNotify(same(subscription), same(req), eq("runtime"), any(RuntimeException.class));
      verify(subscription).notifyError(any(Exception.class));
    }
  }
}
