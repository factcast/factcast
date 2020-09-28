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
package org.factcast.store.pgsql.registry.validation;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.SchemaRegistry;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.factcast.store.pgsql.registry.validation.schema.store.InMemSchemaStoreImpl;
import org.factcast.store.pgsql.registry.validation.schema.store.PgSchemaStoreImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class FactValidatorConfiguration {
  @Bean
  public SchemaStore schemaStore(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull PgConfigurationProperties props,
      @NonNull RegistryMetrics registryMetrics,
      @Autowired(required = false) SpringLiquibase unused) {
    if (props.isValidationEnabled() && props.isPersistentRegistry())
      return new PgSchemaStoreImpl(jdbcTemplate, registryMetrics);

    // otherwise
    return new InMemSchemaStoreImpl(registryMetrics);
  }

  @Bean
  public FactValidator factValidator(
      PgConfigurationProperties props,
      SchemaRegistry registry,
      @NonNull RegistryMetrics registryMetrics) {
    if (props.isValidationEnabled()) return new FactValidator(props, registry, registryMetrics);
    else return null;
  }

  @Bean
  @ConditionalOnProperty(name = "factcast.store.pgsql.schemaRegistryUrl")
  public FactValidationAspect factValidationAspect(
      PgConfigurationProperties props, FactValidator v) {
    return new FactValidationAspect(v);
  }
}
