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

import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.PgFactStoreConfiguration;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.factcast.store.internal.tail.*;
import org.factcast.test.PostgresVersion;
import org.mockito.Mockito;
import org.postgresql.Driver;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SuppressWarnings("resource")
@Configuration
@Import(PgFactStoreConfiguration.class)
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  TransactionAutoConfiguration.class,
  LiquibaseAutoConfiguration.class
})
@Slf4j
public class PgTestConfiguration {

  static {
    String url = System.getenv("pg_url");
    if (url == null) {
      String version = PostgresVersion.get();
      log.info("Trying to start postgres testcontainer version {}", version);
      PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:" + version);
      postgres.start();
      url = postgres.getJdbcUrl();
      System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
      System.setProperty(
          "spring.datasource.url", url + "?socketTimeout=0&preparedStatementCacheSize=0");
      System.setProperty("spring.datasource.username", postgres.getUsername());
      System.setProperty("spring.datasource.password", postgres.getPassword());
      System.setProperty("spring.datasource.maxActive", "20");
      System.setProperty("spring.datasource.tomcat.connectionProperties", "foo=bar;");
    } else {
      log.info("Using predefined external postgres URL: " + url);
      // use predefined url
      System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
      System.setProperty("spring.datasource.url", url);
    }
  }

  @Bean
  @Primary
  public PgMetrics pgMetrics(@NonNull MeterRegistry registry) {
    return Mockito.spy(new PgMetrics(registry));
  }

  @Bean
  JSEngineFactory engineFactory() {
    return new GraalJSEngineFactory();
  }
}
