package org.factcast.store.pgsql.internal;

import java.util.concurrent.Executors;

import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.factcast.core.store.FactStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfiguration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.codahale.metrics.MetricRegistry;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.impossibl.postgres.jdbc.PGDriver;

/**
 * Main @Configuration class for a PGFactStore
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Configuration
@EnableScheduling
@Import(SchedulingConfiguration.class)
public class PGFactStoreInternalConfiguration {
    static {
        final String DRIVER_PROPERTY = "spring.datasource.driverClassName";

        if (System.getProperty(DRIVER_PROPERTY) == null) {
            System.setProperty(DRIVER_PROPERTY, PGDriver.class.getCanonicalName());
        }
    }

    @Bean
    @ConditionalOnMissingBean(EventBus.class)
    public EventBus eventBus() {
        return new AsyncEventBus(this.getClass().getSimpleName(), Executors.newCachedThreadPool());
    }

    @Bean
    public PGFactIdToSerMapper pgEventIdToSerMapper(JdbcTemplate jdbcTemplate) {
        return new PGFactIdToSerMapper(jdbcTemplate);
    }

    @Bean
    public PGListener pgSqlListener(EventBus eventBus,
            EnvironmentPGConnectionSupplier connectionSupplier, MetricRegistry registry) {
        return new PGListener(connectionSupplier, eventBus, new ConnectionTester(registry));
    }

    @Bean
    public EnvironmentPGConnectionSupplier environmentPGConnectionSupplier() {
        return new EnvironmentPGConnectionSupplier();
    }

    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Bean
    public FactStore factStore(JdbcTemplate jdbcTemplate, PGSubscriptionFactory queryProvider,
            MetricRegistry metricsRegistry) {
        return new PGFactStore(jdbcTemplate, queryProvider, metricsRegistry);
    }

    @Bean
    public PGSubscriptionFactory pgSubscriptionFactory(JdbcTemplate jdbcTemplate, EventBus eventBus,
            PGFactIdToSerMapper serMapper, PGLatestSerialFetcher fetcher) {
        return new PGSubscriptionFactory(jdbcTemplate, eventBus, serMapper, fetcher);
    }

    @Bean
    public PGLatestSerialFetcher pgLatestSerialFetcher(JdbcTemplate jdbcTemplate) {
        return new PGLatestSerialFetcher(jdbcTemplate);
    }

    @Bean
    @Primary
    public javax.sql.DataSource datasource(EnvironmentPGConnectionSupplier env) {
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        PoolProperties p = new PoolProperties();
        p.setUrl(env.url());
        p.setDriverClassName(PGDriver.class.getCanonicalName());
        p.setInitialSize(32);
        p.setJmxEnabled(false);
        p.setTestOnBorrow(true);
        p.setTestOnReturn(true);
        p.setValidationQuery("SELECT 1");
        p.setTimeBetweenEvictionRunsMillis(30000);
        p.setMaxActive(64);
        p.setMinIdle(5);
        p.setMaxIdle(32);

        p.setRemoveAbandoned(false);
        p.setRemoveAbandonedTimeout(60 * 60 * 24);
        p.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;"
                + "org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer");
        ds.setPoolProperties(p);
        return ds;
    }
}
