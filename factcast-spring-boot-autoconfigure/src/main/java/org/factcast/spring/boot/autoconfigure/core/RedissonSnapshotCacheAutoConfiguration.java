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

import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.redisson.RedissonSnapshotCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import lombok.Generated;

@Configuration
@ConditionalOnClass({ RedissonSnapshotCache.class, RedissonClient.class })
@ConditionalOnMissingBean(SnapshotCache.class)
@Generated
public class RedissonSnapshotCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Order(100)
    public SnapshotCache snapshotCache(RedissonClient redisson) {
        return new RedissonSnapshotCache(redisson);
    }
}
