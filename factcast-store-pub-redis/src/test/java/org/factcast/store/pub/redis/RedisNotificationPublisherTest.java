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
package org.factcast.store.pub.redis;

import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import org.factcast.store.internal.notification.*;
import org.factcast.store.redis.RedisPubSubConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;

@ExtendWith(MockitoExtension.class)
class RedisNotificationPublisherTest {

  @Mock private RedissonClient redis;
  @Spy private EventBus bus = new EventBus();
  @Mock private RTopic notificationTopic;
  @InjectMocks private RedisNotificationPublisher underTest;

  @Nested
  class WhenAfteringSingletonsInstantiated {
    @BeforeEach
    void setup() {}

    @Test
    void registers() {
      underTest.afterSingletonsInstantiated();
      verify(bus).register(underTest);
    }
  }

  @Nested
  class WhenOning {
    @Mock private StoreNotification n;
    @Mock private RTopic topic;

    @BeforeEach
    void setup() {}

    @Test
    void exports() {

      when(redis.getTopic(RedisPubSubConstants.TOPIC)).thenReturn(topic);
      StoreNotification probe = new SchemaStoreChangeNotification("x", "y", 1, 0);
      underTest.afterSingletonsInstantiated();
      underTest.on(probe);

      verify(topic).publish(probe);
    }

    @Test
    void doesNotExportInternalOnes() {

      when(redis.getTopic(RedisPubSubConstants.TOPIC)).thenReturn(topic);
      StoreNotification probe = SchemaStoreChangeNotification.internal();
      underTest.afterSingletonsInstantiated();
      underTest.on(probe);

      verifyNoInteractions(topic);
    }

    @Test
    void wiresToBusProperly() {

      when(redis.getTopic(RedisPubSubConstants.TOPIC)).thenReturn(topic);
      StoreNotification probe = new SchemaStoreChangeNotification("x", "y", 1, 0);
      underTest.afterSingletonsInstantiated();
      bus.post(probe);

      verify(topic).publish(probe);
    }
  }
}
