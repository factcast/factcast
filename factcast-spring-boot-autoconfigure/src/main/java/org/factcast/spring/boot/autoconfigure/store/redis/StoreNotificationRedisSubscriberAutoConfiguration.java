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
package org.factcast.spring.boot.autoconfigure.store.redis;

import com.google.common.eventbus.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgFactStore;
import org.factcast.store.internal.notification.StoreNotificationSubscriber;
import org.factcast.store.sub.redis.RedisNotificationSubscriber;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnClass({RedisNotificationSubscriber.class, PgFactStore.class})
public class StoreNotificationRedisSubscriberAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(StoreNotificationSubscriber.class)
  public StoreNotificationSubscriber redisNotificationSubscriber(
      RedissonClient redis, EventBus bus) {
    log.info("Configuring {}", RedisNotificationSubscriber.class.getSimpleName());
    return new RedisNotificationSubscriber(redis, bus);
  }
}
