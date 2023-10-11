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
import org.factcast.core.FactCast;
import org.factcast.core.snap.FactCastSnapshotCache;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.store.FactStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnClass(FactCast.class)
@Generated
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
public class FactCastSnapshotCacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SnapshotCache snapshotCache(FactStore store) {
    return new FactCastSnapshotCache(store);
  }
}
