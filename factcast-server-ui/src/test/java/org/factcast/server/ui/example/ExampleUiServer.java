/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.example;

import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.server.security.CommonSecurityConfig;
import org.factcast.server.ui.config.SecurityConfig;
import org.factcast.server.ui.config.UIConfig;
import org.factcast.server.ui.plugins.JsonEntryMetaData;
import org.factcast.server.ui.plugins.JsonPayload;
import org.factcast.server.ui.plugins.JsonViewPlugin;
import org.factcast.store.PgFactStoreConfiguration;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.postgresql.Driver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

@Slf4j
@SpringBootApplication
@Import({
  UIConfig.class,
  SecurityConfig.class,
  PgFactStoreConfiguration.class,
  CommonSecurityConfig.class
})
public class ExampleUiServer {
  @Bean
  public JSEngineFactory jsEngineFactory() {
    return new GraalJSEngineFactory();
  }

  @Bean
  public JsonViewPlugin testPlugin() {
    return new JsonViewPlugin() {
      @Override
      public void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
        final var paths = payload.findPaths("$..firstName");

        paths.forEach(
            p -> {
              final var name = payload.read(p, String.class);

              jsonEntryMetaData.annotatePayload(p, "Name: " + name);
              jsonEntryMetaData.addPayloadHoverContent(p, "J. Edgar Hoover: " + name);
            });
      }

      @Override
      public boolean isReady() {
        return true;
      }
    };
  }

  @Bean
  public JsonViewPlugin hoverOnlyPlugin() {
    return new JsonViewPlugin() {
      @Override
      public void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
        final var paths = payload.findPaths("$..lastName");

        paths.forEach(
            p -> {
              final var name = payload.read(p, String.class);
              jsonEntryMetaData.addPayloadHoverContent(p, "J. Edgar Hoover: " + name);
            });
      }

      @Override
      public boolean isReady() {
        return true;
      }
    };
  }

  public static void main(String[] args) {
    startPostgresContainer();

    SpringApplication.run(ExampleUiServer.class, args);
  }

  private static void startPostgresContainer() {
    log.info("Trying to start postgres testcontainer");
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.2");
    postgres.start();
    String url = postgres.getJdbcUrl();
    System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
    System.setProperty("spring.datasource.url", url);
    System.setProperty("spring.datasource.username", postgres.getUsername());
    System.setProperty("spring.datasource.password", postgres.getPassword());
  }
}
