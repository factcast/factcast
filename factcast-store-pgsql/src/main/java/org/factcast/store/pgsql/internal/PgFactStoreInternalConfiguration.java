/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal;

import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import javax.sql.DataSource;

import org.factcast.core.store.FactStore;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.catchup.paged.PgPagedCatchUpFactory;
import org.factcast.store.pgsql.internal.listen.PgConnectionSupplier;
import org.factcast.store.pgsql.internal.listen.PgConnectionTester;
import org.factcast.store.pgsql.internal.listen.PgListener;
import org.factcast.store.pgsql.internal.lock.AdvisoryWriteLock;
import org.factcast.store.pgsql.internal.lock.FactTableWriteLock;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.NonNull;

/**
 * Main @Configuration class for a PGFactStore
 *
 * @author uwe.schaefer@mercateo.com
 */
@SuppressWarnings("UnstableApiUsage")
@Configuration
@EnableTransactionManagement
public class PgFactStoreInternalConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventBus.class)
    public EventBus eventBus() {
        return new AsyncEventBus(this.getClass().getSimpleName(), Executors.newCachedThreadPool());
    }

    @Bean
    public PgCatchupFactory pgCatchupFactory(PgConfigurationProperties props, JdbcTemplate jdbc,
            PgFactIdToSerialMapper serMapper) {
        // noinspection SwitchStatementWithTooFewBranches
        switch (props.getCatchupStrategy()) {
        case PAGED:
            return new PgPagedCatchUpFactory(jdbc, props, serMapper);
        default:
            throw new IllegalArgumentException("Unmapped Strategy: " + props.getCatchupStrategy());
        }
    }

    @Bean
    public FactStore factStore(JdbcTemplate jdbcTemplate, PgSubscriptionFactory subscriptionFactory,
            PgTokenStore tokenStore, FactTableWriteLock lock, MeterRegistry registry) {
        return new PgFactStore(jdbcTemplate, subscriptionFactory, tokenStore, lock, registry);
    }

    @Bean
    public PgSubscriptionFactory pgSubscriptionFactory(JdbcTemplate jdbcTemplate, EventBus eventBus,
            PgFactIdToSerialMapper pgFactIdToSerialMapper,
            PgLatestSerialFetcher pgLatestSerialFetcher, PgCatchupFactory pgCatchupFactory) {
        return new PgSubscriptionFactory(jdbcTemplate, eventBus, pgFactIdToSerialMapper,
                pgLatestSerialFetcher, pgCatchupFactory);

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
    public PgListener pgListener(@NonNull PgConnectionSupplier pgConnectionSupplier,
            @NonNull EventBus eventBus, @NonNull Predicate<Connection> predicate) {
        return new PgListener(pgConnectionSupplier, eventBus, predicate);
    }

    @Bean
    public PgFactIdToSerialMapper pgFactIdToSerialMapper(JdbcTemplate jdbcTemplate) {
        return new PgFactIdToSerialMapper(jdbcTemplate);
    }

    @Bean
    public PgLatestSerialFetcher pgLatestSerialFetcher(JdbcTemplate jdbcTemplate) {
        return new PgLatestSerialFetcher(jdbcTemplate);
    }

    @Bean
    public PgTokenStore pgTokenStore(JdbcTemplate jdbcTemplate) {
        return new PgTokenStore(jdbcTemplate);
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

}
