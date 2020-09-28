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
package org.factcast.store.pgsql.registry.transformation;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.SchemaRegistry;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.transformation.cache.InMemTransformationCache;
import org.factcast.store.pgsql.registry.transformation.cache.PgTransformationCache;
import org.factcast.store.pgsql.registry.transformation.cache.TransformationCache;
import org.factcast.store.pgsql.registry.transformation.chains.NashornTransformer;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChains;
import org.factcast.store.pgsql.registry.transformation.chains.Transformer;
import org.factcast.store.pgsql.registry.transformation.store.InMemTransformationStoreImpl;
import org.factcast.store.pgsql.registry.transformation.store.PgTransformationStoreImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class TransformationConfiguration {
  @Bean
  public TransformationStore transformationStore(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull PgConfigurationProperties props,
      @NonNull RegistryMetrics registryMetrics,
      @Autowired(required = false) SpringLiquibase unused) {
    if (props.isValidationEnabled() && props.isPersistentRegistry())
      return new PgTransformationStoreImpl(jdbcTemplate, registryMetrics);

    // otherwise
    return new InMemTransformationStoreImpl(registryMetrics);
  }

  @Bean
  public TransformationCache transformationCache(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull PgConfigurationProperties props,
      @NonNull RegistryMetrics registryMetrics,
      @Autowired(required = false) SpringLiquibase unused) {
    if (props.isValidationEnabled() && props.isPersistentTransformationCache())
      return new PgTransformationCache(jdbcTemplate, registryMetrics);

    // otherwise
    return new InMemTransformationCache(
        props.getInMemTransformationCacheCapacity(), registryMetrics);
  }

  @Bean
  public TransformationChains transformationChains(
      @NonNull SchemaRegistry r, @NonNull RegistryMetrics registryMetrics) {
    return new TransformationChains(r, registryMetrics);
  }

  @Bean
  public Transformer transformer() {
    // TODO should test for Graal here, as nashorn is deprecated
    return new NashornTransformer();
  }

  @Bean
  public FactTransformersFactory factTransformersFactory(
      FactTransformerService trans, RegistryMetrics registryMetrics) {
    return new FactTransformersFactoryImpl(trans, registryMetrics);
  }

  @Bean
  public FactTransformerService factTransformerService(
      TransformationChains chains,
      Transformer trans,
      TransformationCache cache,
      RegistryMetrics registryMetrics) {
    return new FactTransformerServiceImpl(chains, trans, cache, registryMetrics);
  }

  @Bean
  public TransformationCacheCompactor transformationCacheCompactor(
      TransformationCache cache, PgConfigurationProperties props) {
    return new TransformationCacheCompactor(cache, props.getDeleteTransformationsStaleForDays());
  }
}
