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
package org.factcast.itests.transformation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Spring boot starter for running a factcast server.
 *
 * <p>This should contain a pgsql backend and grpc API frontend.
 *
 * @author uwe.schaefer@mercateo.com
 */
@SuppressWarnings("ALL")
@SpringBootApplication
@Slf4j
public class ExampleServerWithPostgresContainer {

  public static void main(String[] args) {
    SpringApplication.run(ExampleServerWithPostgresContainer.class, args);
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public IntegrationTestContext integrationTestContext() {
    return new IntegrationTestContext();
  }
}
