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
package org.factcast.store.internal;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.inmemory.InMemoryLockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode;
import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.TokenStore;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.store.IsReadAndWriteEnv;
import org.factcast.store.IsReadOnlyEnv;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.catchup.fetching.PgFetchingCatchUpFactory;
import org.factcast.store.internal.catchup.tmppaged.PgTmpPagedCatchUpFactory;
import org.factcast.store.internal.check.IndexCheck;
import org.factcast.store.internal.filter.blacklist.*;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.listen.PgConnectionTester;
import org.factcast.store.internal.listen.PgListener;
import org.factcast.store.internal.lock.AdvisoryWriteLock;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.snapcache.SnapshotCache;
import org.factcast.store.internal.snapcache.SnapshotCacheConfiguration;
import org.factcast.store.internal.tail.PGTailIndexingConfiguration;
import org.factcast.store.internal.telemetry.FactStreamTelemetryPublisher;
import org.factcast.store.registry.PgSchemaStoreChangeListener;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.SchemaRegistryConfiguration;
import org.factcast.store.registry.transformation.cache.PgTransformationStoreChangeListener;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main @Configuration class for a PGFactStore
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
@Configuration
@EnableTransactionManagement
@EnableRetry
@EnableScheduling
// not that InterceptMode.PROXY_SCHEDULER does not work when wrapped at runtime (by opentelemetry
// for instance)
@EnableSchedulerLock(defaultLockAtMostFor = "PT30m", interceptMode = InterceptMode.PROXY_METHOD)
@Import({
  SchemaRegistryConfiguration.class,
  SnapshotCacheConfiguration.class,
  PGTailIndexingConfiguration.class
})
public class PgFactStoreInternalConfiguration {

  @Bean
  @ConditionalOnMissingBean(EventBus.class)
  public EventBus eventBus() {
    return new AsyncEventBus(getClass().getSimpleName(), Executors.newCachedThreadPool());
  }

  @Bean
  public PgCatchupFactory pgCatchupFactory(
      StoreConfigurationProperties props,
      PgConnectionSupplier supp,
      PgMetrics metrics,
      FactTransformerService transformerService) {
    switch (props.getCatchupStrategy()) {
      case PAGED:
        return new PgTmpPagedCatchUpFactory(supp, props, metrics, transformerService);
      case FETCHING:
        return new PgFetchingCatchUpFactory(supp, props, metrics, transformerService);
      default:
        throw new IllegalArgumentException("Unmapped Strategy: " + props.getCatchupStrategy());
    }
  }

  @Bean
  public PgMetrics pgMetrics(@NonNull MeterRegistry registry) {
    return new PgMetrics(registry);
  }

  @Bean
  public FactStreamTelemetryPublisher telemetryPublisher() {
    return new FactStreamTelemetryPublisher();
  }

  @Bean
  public FactStore factStore(
      JdbcTemplate jdbcTemplate,
      PgSubscriptionFactory subscriptionFactory,
      TokenStore tokenStore,
      SchemaRegistry schemaRegistry,
      FactTableWriteLock lock,
      FactTransformerService factTransformerService,
      PgFactIdToSerialMapper pgFactIdToSerialMapper,
      SnapshotCache snapCache,
      PgMetrics pgMetrics,
      StoreConfigurationProperties props,
      PlatformTransactionManager platformTransactionManager) {
    return new PgFactStore(
        jdbcTemplate,
        subscriptionFactory,
        tokenStore,
        schemaRegistry,
        lock,
        factTransformerService,
        pgFactIdToSerialMapper,
        snapCache,
        pgMetrics,
        props,
        platformTransactionManager);
  }

  @Bean
  public PgSubscriptionFactory pgSubscriptionFactory(
      JdbcTemplate jdbcTemplate,
      EventBus eventBus,
      PgFactIdToSerialMapper pgFactIdToSerialMapper,
      PgLatestSerialFetcher pgLatestSerialFetcher,
      StoreConfigurationProperties props,
      PgCatchupFactory pgCatchupFactory,
      FastForwardTarget target,
      PgMetrics metrics,
      Blacklist blacklist,
      JSEngineFactory ef,
      FactTransformerService transformerService,
      FactStreamTelemetryPublisher telemetryPublisher) {
    return new PgSubscriptionFactory(
        jdbcTemplate,
        eventBus,
        pgFactIdToSerialMapper,
        pgLatestSerialFetcher,
        props,
        pgCatchupFactory,
        target,
        metrics,
        blacklist,
        transformerService,
        ef,
        telemetryPublisher);
  }

  @Bean
  public PgConnectionSupplier pgConnectionSupplier(DataSource ds) {
    return new PgConnectionSupplier(ds);
  }

  @Bean
  public PgConnectionTester pgConnectionTester() {
    return new PgConnectionTester();
  }

  @Bean
  public PgListener pgListener(
      @NonNull PgConnectionSupplier pgConnectionSupplier,
      @NonNull EventBus eventBus,
      @NonNull StoreConfigurationProperties props,
      PgMetrics metrics) {
    return new PgListener(pgConnectionSupplier, eventBus, props, metrics);
  }

  @Bean
  public PgFactIdToSerialMapper pgFactIdToSerialMapper(
      JdbcTemplate jdbcTemplate, PgMetrics metrics, MeterRegistry registry) {
    return new PgFactIdToSerialMapper(jdbcTemplate, metrics, registry);
  }

  @Bean
  public PgLatestSerialFetcher pgLatestSerialFetcher(JdbcTemplate jdbcTemplate) {
    return new PgLatestSerialFetcher(jdbcTemplate);
  }

  @Bean
  @IsReadAndWriteEnv
  public TokenStore pgTokenStore(JdbcTemplate jdbcTemplate, PgMetrics metrics) {
    return new PgTokenStore(jdbcTemplate, metrics);
  }

  @Bean
  @IsReadOnlyEnv
  public TokenStore roTokenStore() {
    return new ReadOnlyTokenStore();
  }

  @Bean
  public FactTableWriteLock factTableWriteLock(JdbcTemplate tpl) {
    return new AdvisoryWriteLock(tpl);
  }

  @Bean
  public PlatformTransactionManager txManager(DataSource ds) {
    return new DataSourceTransactionManager(ds);
  }

  /**
   * @return A fallback {@code MeterRegistry} in case none is configured.
   */
  @Bean
  @ConditionalOnMissingBean
  public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  @IsReadAndWriteEnv
  public LockProvider lockProvider(DataSource dataSource) {
    log.debug("Configuring lock provider: JDBC.");

    var config =
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build();

    return new KeepAliveLockProvider(
        new JdbcTemplateLockProvider(config), Executors.newSingleThreadScheduledExecutor());
  }

  @Bean
  @IsReadOnlyEnv
  public LockProvider inMemoryLockProvider() {
    log.debug("Configuring lock provider: IN MEMORY.");

    return new KeepAliveLockProvider(
        new InMemoryLockProvider(), Executors.newSingleThreadScheduledExecutor());
  }

  @Bean
  public IndexCheck indexCheck(JdbcTemplate jdbcTemplate) {
    return new IndexCheck(jdbcTemplate);
  }

  @Bean
  public Blacklist blacklist() {
    return new Blacklist();
  }

  @Bean
  @ConditionalOnProperty(value = "factcast.type", matchIfMissing = true)
  public BlacklistDataProvider blacklistProvider(
      ResourceLoader resourceLoader,
      Blacklist blacklist,
      EventBus eventBus,
      JdbcTemplate jdbc,
      BlacklistConfigurationProperties blacklistConfiguration) {
    switch (blacklistConfiguration.getType()) {
      case POSTGRES:
        return new PgBlacklistDataProvider(eventBus, jdbc, blacklist);
      case RESOURCE:
        return new ResourceBasedBlacklistDataProvider(
            resourceLoader, blacklistConfiguration, blacklist);
      default:
        log.warn(
            "No Provider found for blacklist type {}. Using default postgres provider.",
            blacklistConfiguration.getType());
        return new PgBlacklistDataProvider(eventBus, jdbc, blacklist);
    }
  }

  @Bean
  public PgSchemaStoreChangeListener pgSchemaStoreChangeListener(
      EventBus bus, SchemaRegistry registry) {
    return new PgSchemaStoreChangeListener(bus, registry);
  }

  @Bean
  public PgTransformationStoreChangeListener pgTransformationStoreChangeListener(
      EventBus bus,
      TransformationCache transformationCache,
      TransformationChains transformationChains) {
    return new PgTransformationStoreChangeListener(bus, transformationCache, transformationChains);
  }

  // we create a custom SpringLiquibase bean to avoid autoconfiguration and configure it to not run
  @Bean
  @IsReadOnlyEnv
  public SpringLiquibase springLiquibase() {
    final var liquibase = new SpringLiquibase();
    liquibase.setShouldRun(false);
    return liquibase;
  }
}
