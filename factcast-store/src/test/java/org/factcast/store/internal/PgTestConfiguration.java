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
import org.mockito.Mockito;
import org.postgresql.Driver;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

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
      log.info("Trying to start postgres testcontainer");
      PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:11.5");
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
  @Primary
  public PgMetrics pgMetrics(@NonNull MeterRegistry registry) {
    return Mockito.spy(new PgMetrics(registry));
  }

  @Bean
  JSEngineFactory engineFactory() {
    return new GraalJSEngineFactory();
  }
}
