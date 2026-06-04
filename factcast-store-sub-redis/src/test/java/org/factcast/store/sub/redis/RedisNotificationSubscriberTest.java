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
package org.factcast.store.sub.redis;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class RedisNotificationSubscriberTest {
  @Mock private RTopic topic;

  @Mock RedissonClient redis;
  @Spy EventBus bus = new EventBus();

  @InjectMocks RedisNotificationSubscriber underTest;

  @BeforeEach
  void setup() {
    when(redis.getTopic(RedisPubSubConstants.TOPIC)).thenReturn(topic);
  }

  @Nested
  class WhenAfteringSingletonsInstantiated {

    @Test
    void listens() {

      underTest.afterSingletonsInstantiated();
      verify(topic).addListener(StoreNotification.class, underTest);
    }
  }

  @Nested
  class WhenOning {

    @Test
    void exports() {
      StoreNotification probe = new SchemaStoreChangeNotification("x", "y", 1, 0);
      underTest.afterSingletonsInstantiated();
      underTest.onMessage(RedisPubSubConstants.TOPIC, probe);

      verify(bus).post(probe);
    }
  }
}
