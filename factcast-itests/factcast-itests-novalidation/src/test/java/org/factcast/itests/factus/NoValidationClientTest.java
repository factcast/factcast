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
package org.factcast.itests.factus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ContextConfiguration(classes = NoValidationClient.class)
@Testcontainers
@IntegrationTest
@Slf4j
public class NoValidationClientTest {

  static final Network _docker_network = Network.newNetwork();

  @Container
  static final PostgreSQLContainer _database_container =
      new PostgreSQLContainer<>("postgres:" + System.getProperty("postgres.version", "11.5"))
          .withDatabaseName("fc")
          .withUsername("fc")
          .withPassword("fc")
          .withNetworkAliases("db")
          .withNetwork(_docker_network);

  @Container
  static final GenericContainer _factcast_container =
      new GenericContainer<>("factcast/factcast:latest")
          .withExposedPorts(9090)
          .withFileSystemBind("./config", "/config/")
          .withEnv("grpc.server.port", "9090")
          .withEnv("factcast.security.enabled", "false")
          .withEnv("spring.datasource.url", "jdbc:postgresql://db/fc?user=fc&password=fc")
          .withNetwork(_docker_network)
          .dependsOn(_database_container)
          .withLogConsumer(new Slf4jLogConsumer(log))
          .waitingFor(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));

  @BeforeAll
  public static void startContainers() throws InterruptedException {
    System.setProperty(
        "grpc.client.factstore.address",
        "static://"
            + _factcast_container.getHost()
            + ":"
            + _factcast_container.getMappedPort(9090));
  }

  @Autowired FactCast fc;

  @Test
  public void simplePublishRoundtrip() throws Exception {
    Fact fact = Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}");
    fc.publish(fact);

    try (Subscription sub =
        fc.subscribe(
                SubscriptionRequest.catchup(FactSpec.ns("smoke")).fromScratch(),
                f -> {
                  assertEquals(fact.ns(), f.ns());
                  assertEquals(fact.type(), f.type());
                  assertEquals(fact.id(), f.id());
                })
            .awaitCatchup(1000)) {
      // empty block
    }
  }
}
