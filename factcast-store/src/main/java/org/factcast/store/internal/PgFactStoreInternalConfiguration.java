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

import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.catchup.fetching.PgFetchingCatchUpFactory;
import org.factcast.store.internal.catchup.tmppaged.PgTmpPagedCatchUpFactory;
import org.factcast.store.internal.check.IndexCheck;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.listen.PgConnectionTester;
import org.factcast.store.internal.listen.PgListener;
import org.factcast.store.internal.lock.AdvisoryWriteLock;
import org.factcast.store.internal.lock.FactTableWriteLock;
import org.factcast.store.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.factcast.store.internal.snapcache.PgSnapshotCache;
import org.factcast.store.internal.snapcache.PgSnapshotCacheConfiguration;
import org.factcast.store.internal.tail.PGTailIndexingConfiguration;
import org.factcast.store.registry.SchemaRegistryConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode;

/**
 * Main @Configuration class for a PGFactStore
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("UnstableApiUsage")
@Slf4j
@Configuration
@EnableTransactionManagement
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30m", interceptMode = InterceptMode.PROXY_SCHEDULER)
@Import({
  SchemaRegistryConfiguration.class,
  PgSnapshotCacheConfiguration.class,
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
      PgFactIdToSerialMapper serMapper) {
    // noinspection SwitchStatementWithTooFewBranches
    switch (props.getCatchupStrategy()) {
      case PAGED:
        return new PgTmpPagedCatchUpFactory(supp, props);
      case FETCHING:
        return new PgFetchingCatchUpFactory(supp, props);
      default:
        throw new IllegalArgumentException("Unmapped Strategy: " + props.getCatchupStrategy());
    }
  }

  @Bean
  public PgMetrics pgMetrics(@NonNull MeterRegistry registry) {
    return new PgMetrics(registry);
  }

  @Bean
  public FactStore factStore(
      JdbcTemplate jdbcTemplate,
      PgSubscriptionFactory subscriptionFactory,
      PgTokenStore tokenStore,
      FactTableWriteLock lock,
      FactTransformerService factTransformerService,
      PgFactIdToSerialMapper pgFactIdToSerialMapper,
      PgSnapshotCache snapCache,
      PgMetrics pgMetrics) {
    return new PgFactStore(
        jdbcTemplate,
        subscriptionFactory,
        tokenStore,
        lock,
        factTransformerService,
        pgFactIdToSerialMapper,
        snapCache,
        pgMetrics);
  }

  @Bean
  public PgSubscriptionFactory pgSubscriptionFactory(
      JdbcTemplate jdbcTemplate,
      EventBus eventBus,
      PgFactIdToSerialMapper pgFactIdToSerialMapper,
      PgLatestSerialFetcher pgLatestSerialFetcher,
      PgCatchupFactory pgCatchupFactory,
      FactTransformersFactory transformerFactory,
      FastForwardTarget target,
      PgMetrics metrics) {
    return new PgSubscriptionFactory(
        jdbcTemplate,
        eventBus,
        pgFactIdToSerialMapper,
        pgLatestSerialFetcher,
        pgCatchupFactory,
        transformerFactory,
        target,
        metrics);
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
  public PgTokenStore pgTokenStore(JdbcTemplate jdbcTemplate, PgMetrics metrics) {
    return new PgTokenStore(jdbcTemplate, metrics);
  }

  @Bean
  public FactTableWriteLock factTableWriteLock(JdbcTemplate tpl) {
    return new AdvisoryWriteLock(tpl);
  }

  @Bean
  public PlatformTransactionManager txManager(DataSource ds) {
    return new DataSourceTransactionManager(ds);
  }

  /** @return A fallback {@code MeterRegistry} in case none is configured. */
  @Bean
  @ConditionalOnMissingBean
  public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    log.debug("Configuring lock provider.");
    var config =
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build();

    return new JdbcTemplateLockProvider(config);
  }

  @Bean
  public IndexCheck indexCheck(JdbcTemplate jdbcTemplate) {
    return new IndexCheck(jdbcTemplate);
  }
}
