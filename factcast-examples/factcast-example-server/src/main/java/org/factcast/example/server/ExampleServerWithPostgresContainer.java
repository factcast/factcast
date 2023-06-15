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
package org.factcast.example.server;

import lombok.extern.slf4j.Slf4j;
import org.postgresql.Driver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Spring boot starter for running a factcast server.
 *
 * <p>This should contain a pgsql backend and grpc API frontend.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("ALL")
@SpringBootApplication
@Slf4j
public class ExampleServerWithPostgresContainer {

  public static void main(String[] args) {
    // you will want to replace this with RDS, a local pgsql database or
    // whatever your usecase is.
    startPostgresContainer();

    SpringApplication.run(ExampleServerWithPostgresContainer.class, args);
  }

  private static void startPostgresContainer() {
    log.info("Trying to start postgres testcontainer");
    PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:" + System.getProperty("postgres.version", "11.5"));
    postgres.start();
    String url = postgres.getJdbcUrl();
    System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
    System.setProperty("spring.datasource.url", url);
    System.setProperty("spring.datasource.username", postgres.getUsername());
    System.setProperty("spring.datasource.password", postgres.getPassword());
  }
}
