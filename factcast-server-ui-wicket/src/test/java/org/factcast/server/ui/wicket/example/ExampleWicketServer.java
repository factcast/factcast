/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.server.ui.wicket.example;

import java.util.Map;
import org.factcast.server.security.CommonSecurityConfiguration;
import org.factcast.server.ui.wicket.WicketUIConfiguration;
import org.factcast.store.PgFactStoreConfiguration;
import org.factcast.test.PostgresVersion;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootApplication
@Import({
  WicketUIConfiguration.class,
  PgFactStoreConfiguration.class,
  CommonSecurityConfiguration.class
})
public class ExampleWicketServer {
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:" + PostgresVersion.get());
  }

  public static void main(String[] args) {
    var application = new SpringApplication(ExampleWicketServer.class);
    application.setDefaultProperties(
        Map.of(
            "factcast.security.enabled", "false",
            "factcast.store.schema-registry-url", "classpath:example-registry",
            "factcast.ui.report.store.path", "target/wicket-reports"));
    application.run(args);
  }
}
