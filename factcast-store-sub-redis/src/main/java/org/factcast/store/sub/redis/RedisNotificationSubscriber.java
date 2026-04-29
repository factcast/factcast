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

import com.google.common.eventbus.EventBus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.notification.*;
import org.factcast.store.redis.RedisPubSubConstants;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNotificationSubscriber
    implements SmartInitializingSingleton,
        MessageListener<StoreNotification>,
        StoreNotificationSubscriber {

  @NonNull private final RedissonClient redis;
  @NonNull private final EventBus bus;

  @Override
  public void afterSingletonsInstantiated() {
    redis.getTopic(RedisPubSubConstants.TOPIC).addListener(StoreNotification.class, this);
  }

  @Override
  public void onMessage(CharSequence channel, StoreNotification msg) {
    bus.post(msg);
  }
}
