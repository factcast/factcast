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

import com.google.common.eventbus.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.notification.StoreNotification;
import org.factcast.store.pub.StoreNotificationPublisher;
import org.factcast.store.redis.RedisPubSubConstants;
import org.redisson.api.*;
import org.springframework.beans.factory.SmartInitializingSingleton;

@Slf4j
@RequiredArgsConstructor
public class RedisNotificationPublisher
    implements StoreNotificationPublisher, SmartInitializingSingleton {
  private final RedissonClient redis;
  private final EventBus bus;
  private RTopic notificationTopic;

  @Override
  public void afterSingletonsInstantiated() {
    notificationTopic = redis.getTopic(RedisPubSubConstants.TOPIC);
    bus.register(this);
  }

  @Subscribe
  public void on(StoreNotification n) {
    if (n.distributed()) {
      log.trace("publishing distributed notification: {}", n);
      notificationTopic.publish(n);
    }
  }
}
