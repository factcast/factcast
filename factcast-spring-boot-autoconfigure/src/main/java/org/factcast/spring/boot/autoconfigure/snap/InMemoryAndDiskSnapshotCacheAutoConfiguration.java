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

import org.factcast.core.snap.local.InMemoryAndDiskSnapshotCache;
import org.factcast.core.snap.local.InMemoryAndDiskSnapshotProperties;
import org.factcast.core.snap.local.SnapshotDiskRepository;
import org.factcast.core.snap.local.SnapshotDiskRepositoryImpl;
import org.factcast.factus.Factus;
import org.factcast.factus.snapshot.SnapshotCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass({InMemoryAndDiskSnapshotCache.class, Factus.class})
@ConditionalOnMissingBean(SnapshotCache.class)
@Import({InMemoryAndDiskSnapshotProperties.class})
@AutoConfigureBefore({
  NoSnapshotCacheAutoConfiguration.class,
  InMemorySnapshotCacheAutoConfiguration.class
})
@AutoConfigureAfter(RedissonSnapshotCacheAutoConfiguration.class)
public class InMemoryAndDiskSnapshotCacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SnapshotCache snapshotCache(
      InMemoryAndDiskSnapshotProperties props, SnapshotDiskRepository snapshotDiskRepository) {
    return new InMemoryAndDiskSnapshotCache(props, snapshotDiskRepository);
  }

  @Bean
  @ConditionalOnMissingBean
  public SnapshotDiskRepository snapshotCache(InMemoryAndDiskSnapshotProperties props) {
    return new SnapshotDiskRepositoryImpl(props);
  }
}
