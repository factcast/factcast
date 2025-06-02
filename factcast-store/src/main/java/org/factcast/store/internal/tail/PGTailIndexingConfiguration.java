/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.tail;

import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.IsReadAndWriteEnv;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PGTailIndexingConfiguration {

  @Bean
  @IsReadAndWriteEnv
  public PGTailIndexManager pgTailIndexManager(
      PgConnectionSupplier pgConnectionSupplier,
      StoreConfigurationProperties props,
      PgMetrics pgMetrics) {
    return new PGTailIndexManagerImpl(pgConnectionSupplier, props, pgMetrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public FastForwardTarget fastForwardTarget(JdbcTemplate jdbc) {
    return new MemoizedFastForwardTarget(jdbc);
  }
}
