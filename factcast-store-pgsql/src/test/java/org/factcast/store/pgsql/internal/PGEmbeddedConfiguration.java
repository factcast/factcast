package org.factcast.store.pgsql.internal;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

import org.factcast.store.pgsql.PGConfigurationProperties;
import org.mockito.Mockito;
import org.postgresql.Driver;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

import com.codahale.metrics.MetricRegistry;

import lombok.extern.slf4j.Slf4j;

@Configuration
@ComponentScan(basePackageClasses = PGConfigurationProperties.class)
@Import(PGFactStoreInternalConfiguration.class)
@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        TransactionAutoConfiguration.class })
@Slf4j
public class PGEmbeddedConfiguration {

    static org.apache.tomcat.jdbc.pool.DataSource ds;

    static {
        String url = System.getenv("pg_url");
        if (url == null) {
            log.info("Trying to start postgres testcontainer");
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>();
            postgres.start();
            url = postgres.getJdbcUrl();
            System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
            System.setProperty("spring.datasource.url", url);
            System.setProperty("spring.datasource.username", postgres.getUsername());
            System.setProperty("spring.datasource.password", postgres.getPassword());
        } else {
            log.info("Using predefined external postgres URL: " + url);
            // use predefined url
            System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
            System.setProperty("spring.datasource.url", url);
        }
    }

    @Bean
    public MetricRegistry nopCounterService() {
        return Mockito.mock(MetricRegistry.class, RETURNS_DEEP_STUBS);
    }
}
