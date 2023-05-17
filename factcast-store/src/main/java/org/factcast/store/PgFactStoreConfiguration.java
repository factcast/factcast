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
package org.factcast.store;

import org.factcast.store.internal.PgFactStoreInternalConfiguration;
import org.factcast.store.internal.filter.blacklist.BlacklistConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration to include in order to use a PGFactStore
 *
 * <p>just forwards to {@link PgFactStoreInternalConfiguration}, so that IDEs can still complain
 * about internal references.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Configuration
@EnableConfigurationProperties
@Import(PgFactStoreInternalConfiguration.class)
public class PgFactStoreConfiguration {
  @Bean
  StoreConfigurationProperties storeConfigurationProperties() {
    return new StoreConfigurationProperties();
  }

  @Bean
  PgLegacyConfigurationProperties pgLegacyConfigurationProperties() {
    return new PgLegacyConfigurationProperties();
  }

  @Bean
  BlacklistConfigurationProperties blacklistConfigurationProperties() {
    return new BlacklistConfigurationProperties();
  }
}
