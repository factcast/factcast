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
package org.factcast.itests.store;

import lombok.extern.slf4j.Slf4j;
import org.factcast.test.PostgresVersion;
import org.postgresql.Driver;
import org.testcontainers.containers.PostgreSQLContainer;

@Slf4j
public class IntegrationTestContext {
  public IntegrationTestContext() {
    log.info("Trying to start postgres testcontainer");
    PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:" + PostgresVersion.get());
    postgres.start();
    String url = postgres.getJdbcUrl();
    System.setProperty("spring.datasource.driver-class-name", Driver.class.getName());
    System.setProperty("spring.datasource.url", url);
    System.setProperty("spring.datasource.username", postgres.getUsername());
    System.setProperty("spring.datasource.password", postgres.getPassword());
  }
}
