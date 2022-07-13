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
package org.factcast.store.registry.transformation;

import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.InMemTransformationCache;
import org.factcast.store.registry.transformation.cache.PgTransformationCache;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.JsTransformer;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;
import org.factcast.store.registry.transformation.store.InMemTransformationStoreImpl;
import org.factcast.store.registry.transformation.store.PgTransformationStoreImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class TransformationConfiguration {
  @Bean
  public TransformationStore transformationStore(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull TransactionTemplate txTemplate,
      @NonNull StoreConfigurationProperties props,
      @NonNull RegistryMetrics registryMetrics,
      @Autowired(required = false) SpringLiquibase unused) {
    if (props.isSchemaRegistryConfigured() && props.isPersistentRegistry())
      return new PgTransformationStoreImpl(jdbcTemplate, txTemplate, registryMetrics);

    // otherwise
    return new InMemTransformationStoreImpl(registryMetrics);
  }

  @Bean
  public TransformationCache transformationCache(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull StoreConfigurationProperties props,
      @NonNull RegistryMetrics registryMetrics,
      @Autowired(required = false) SpringLiquibase unused) {
    if (props.isSchemaRegistryConfigured() && props.isPersistentTransformationCache())
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
  public Transformer transformer(@NonNull JSEngineFactory engineFactory) {
    return new JsTransformer(engineFactory);
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
      TransformationCache cache, StoreConfigurationProperties props) {
    return new TransformationCacheCompactor(cache, props.getDeleteTransformationsStaleForDays());
  }
  
 
}
