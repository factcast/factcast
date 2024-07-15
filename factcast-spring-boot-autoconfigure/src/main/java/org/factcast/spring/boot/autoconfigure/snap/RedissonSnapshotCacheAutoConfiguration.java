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
package org.factcast.spring.boot.autoconfigure.snap;

import lombok.NonNull;
import org.factcast.core.snap.redisson.RedissonSnapshotCache;
import org.factcast.core.snap.redisson.RedissonSnapshotProperties;
import org.factcast.factus.snapshot.SnapshotCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass({RedissonSnapshotCache.class, RedissonClient.class})
@ConditionalOnMissingBean(SnapshotCache.class)
@Import({RedissonSnapshotProperties.class})
@AutoConfigureBefore(FactCastSnapshotCacheAutoConfiguration.class)
public class RedissonSnapshotCacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SnapshotCache snapshotCache(
      RedissonClient redisson, @NonNull RedissonSnapshotProperties props) {
    // Initialize codec at container startup to verify existence of required dependencies.
    props.getSnapshotCacheRedissonCodec().codec();
    return new RedissonSnapshotCache(redisson, props);
  }

  // compacting no longer needed with redis as we use EXPIRE instead
}
