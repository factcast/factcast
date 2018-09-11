package org.factcast.store.pgsql.internal;

import static org.mockito.Mockito.*;
import static ru.yandex.qatools.embed.postgresql.distribution.Version.Main.PRODUCTION;

import java.io.IOException;

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

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.Version;

import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Credentials;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Net;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Storage;
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.Timeout;

@Configuration
@ComponentScan(basePackageClasses = PGConfigurationProperties.class)
@Import(PGFactStoreInternalConfiguration.class)
@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        TransactionAutoConfiguration.class })
public class PGEmbeddedConfiguration {

    static org.apache.tomcat.jdbc.pool.DataSource ds;

    static {

        String url = System.getenv("pg_url");

        if (url == null) {
            try {
                PostgresConfig pgConfig =

                        new PostgresConfig(
                                ru.yandex.qatools.embed.postgresql.distribution.Version.V9_6_8,
                                new Net(), new Storage("embedded"), new Timeout(),
                                new Credentials("test", "test"));
                PostgresStarter<PostgresExecutable, PostgresProcess> runtime = PostgresStarter
                        .getDefaultInstance();
                PostgresExecutable exec = runtime.prepare(pgConfig);
                exec.start();

                String host = pgConfig.net().host();
                int port = pgConfig.net().port();
                String dbName = pgConfig.storage().dbName();
                url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
                //

                System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());

                System.setProperty("spring.datasource.username", pgConfig.credentials().username());
                System.setProperty("spring.datasource.password", pgConfig.credentials().password());
                System.setProperty("spring.datasource.host", host);
                System.setProperty("spring.datasource.port", Integer.valueOf(port).toString());

                System.setProperty("spring.datasource.url", url);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
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