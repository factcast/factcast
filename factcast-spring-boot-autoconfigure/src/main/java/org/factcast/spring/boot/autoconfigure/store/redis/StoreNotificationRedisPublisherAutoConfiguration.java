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
import org.factcast.store.pub.StoreNotificationPublisher;
import org.factcast.store.pub.redis.RedisNotificationPublisher;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({RedisNotificationPublisher.class})
public class StoreNotificationRedisPublisherAutoConfiguration {
  @Bean
  @ConditionalOnClass(RedisNotificationPublisher.class)
  @ConditionalOnMissingBean(StoreNotificationPublisher.class)
  public StoreNotificationPublisher redisNotificationPublisher(RedissonClient redis, EventBus bus) {
    return new RedisNotificationPublisher(redis, bus);
  }
}
