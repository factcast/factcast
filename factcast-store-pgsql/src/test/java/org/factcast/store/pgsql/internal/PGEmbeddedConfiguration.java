package org.factcast.store.pgsql.internal;

import static org.mockito.Mockito.*;

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

import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig;

@Configuration
@ComponentScan(basePackageClasses = PGConfigurationProperties.class)
@Import(PGFactStoreInternalConfiguration.class)
@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        TransactionAutoConfiguration.class })
public class PGEmbeddedConfiguration {

    static org.apache.tomcat.jdbc.pool.DataSource ds;

    static {
        try {
            PostgresConfig pgConfig = PostgresConfig.defaultWithDbName("embedded", "test", "test");
            PostgresStarter<PostgresExecutable, PostgresProcess> runtime = PostgresStarter
                    .getDefaultInstance();
            PostgresExecutable exec = runtime.prepare(pgConfig);
            exec.start();

            String host = pgConfig.net().host();
            int port = pgConfig.net().port();
            String dbName = pgConfig.storage().dbName();
            String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
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
    }

    @Bean
    public MetricRegistry nopCounterService() {
        return Mockito.mock(MetricRegistry.class, RETURNS_DEEP_STUBS);
    }
}