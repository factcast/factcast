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

import static org.factcast.store.internal.PgFactStoreInternalConfiguration.P1_CATCHUP_DATASOURCE_BEAN_NAME;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgFactStoreInternalConfiguration;
import org.factcast.store.internal.filter.blacklist.BlacklistConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
@Slf4j
public class PgFactStoreConfiguration {
  @Bean
  StoreConfigurationProperties storeConfigurationProperties() {
    return new StoreConfigurationProperties();
  }

  @Bean
  BlacklistConfigurationProperties blacklistConfigurationProperties() {
    return new BlacklistConfigurationProperties();
  }

  @Bean
  P1CatchupDataSourceProperties p1CatchupDataSourceProperties() {
    return new P1CatchupDataSourceProperties();
  }

  @Bean(value = P1_CATCHUP_DATASOURCE_BEAN_NAME, destroyMethod = "close", autowireCandidate = false)
  @ConfigurationProperties(P1CatchupDataSourceProperties.PROPERTIES_PREFIX)
  @ConditionalOnMissingBean(name = P1_CATCHUP_DATASOURCE_BEAN_NAME)
  @ConditionalOnProperty(prefix = P1CatchupDataSourceProperties.PROPERTIES_PREFIX, name = "url")
  DataSource p1CatchupDataSource(P1CatchupDataSourceProperties properties) {
    log.info(
        "Configuring P1 catchup datasource from {}",
        P1CatchupDataSourceProperties.PROPERTIES_PREFIX);
    return properties.initializeDataSourceBuilder().build();
  }
}
