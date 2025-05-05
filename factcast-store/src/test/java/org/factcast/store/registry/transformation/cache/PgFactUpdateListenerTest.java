/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.store.registry.transformation.cache;

import static org.factcast.store.registry.transformation.cache.PgFactUpdateListener.INFLIGHT_TRANSFORMATIONS_DELAY_SECONDS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.factcast.store.internal.notification.FactUpdateNotification;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgFactUpdateListenerTest {
  @Spy private EventBus bus = new EventBus();

  @Mock private TransformationCache transformationCache;

  @Mock private ScheduledExecutorService executor;

  @InjectMocks PgFactUpdateListener underTest;

  @Captor ArgumentCaptor<Runnable> lambdaCaptor;

  @Nested
  class WhenAfteringSingletonsInstantiated {
    @Test
    void registersOnBus() {
      underTest.afterSingletonsInstantiated();
      verify(bus, times(1)).register(underTest);
    }
  }

  @Nested
  class WhenDisposing {
    @SneakyThrows
    @Test
    void unregisters() {
      underTest.afterSingletonsInstantiated();
      underTest.destroy();
      verify(bus).unregister(underTest);
    }
  }

  @Nested
  class WhenOning {
    private FactUpdateNotification signal;

    private final UUID factId = UUID.randomUUID();

    @Test
    void invalidatesCache() {
      signal = new FactUpdateNotification(factId);
      underTest.on(signal);
      verify(transformationCache, times(1)).invalidateTransformationFor(factId);
    }

    @Test
    void schedulesCacheInvalidationToCoverInflightTransformations() {
      signal = new FactUpdateNotification(factId);
      underTest.on(signal);
      verify(executor)
          .schedule(
              lambdaCaptor.capture(),
              eq(INFLIGHT_TRANSFORMATIONS_DELAY_SECONDS),
              eq(TimeUnit.SECONDS));
      Runnable invalidation = lambdaCaptor.getValue();
      invalidation.run();
      verify(transformationCache, times(2)).invalidateTransformationFor(factId);
    }
  }
}
