/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.spring.boot.autoconfigure.core;

import lombok.Generated;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.redisson.RedissonSnapshotCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({RedissonSnapshotCache.class, RedissonClient.class})
@ConditionalOnMissingBean(SnapshotCache.class)
@Generated
@AutoConfigureOrder(-100)
public class RedissonSnapshotCacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SnapshotCache snapshotCache(
      RedissonClient redisson,
      @Value("${factcast.redis.deleteSnapshotStaleForDays:90}") int retentionTimeInDays) {
    return new RedissonSnapshotCache(redisson, retentionTimeInDays);
  }
}
