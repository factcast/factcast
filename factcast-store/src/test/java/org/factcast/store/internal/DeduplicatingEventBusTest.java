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
package org.factcast.store.internal;

import com.google.common.eventbus.Subscribe;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.factcast.store.internal.notification.SchemaStoreChangeNotification;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeduplicatingEventBusTest {
  Duration MAX_WAIT_MS = Duration.ofMillis(200);
  final DeduplicatingEventBus underTest =
      new DeduplicatingEventBus("poit", Executors.newCachedThreadPool());

  @Nested
  class WhenPosting {

    final AtomicInteger hit = new AtomicInteger(0);
    final Object count =
        new Object() {

          @Subscribe
          void on(Object o) {
            hit.incrementAndGet();
          }
        };

    @BeforeEach
    void setup() {
      underTest.register(count);
    }

    @AfterEach
    void tearDown() {
      underTest.unregister(count);
    }

    @Test
    void passesNonStoreNotifications() {
      for (int i = 0; i < 100; i++) {
        underTest.post(i);
      }

      Awaitility.waitAtMost(MAX_WAIT_MS)
          .untilAsserted(() -> Assertions.assertThat(hit.get()).isEqualTo(100));
    }

    @Test
    void passesNonIdentiofiableNotifications() {
      for (int i = 0; i < 100; i++) {
        underTest.post(SchemaStoreChangeNotification.internal());
      }

      Awaitility.waitAtMost(MAX_WAIT_MS)
          .untilAsserted(() -> Assertions.assertThat(hit.get()).isEqualTo(100));
    }

    @Test
    void dedups() {
      for (int i = 0; i < 100; i++) {
        underTest.post(new SchemaStoreChangeNotification("n1", "t1", i % 10, 0));
      }

      Awaitility.waitAtMost(MAX_WAIT_MS)
          .untilAsserted(() -> Assertions.assertThat(hit.get()).isEqualTo(10));
    }
  }
}
