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
package org.factcast.itests.tls.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.itests.tls.TLSClient;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = TLSClient.class)
@ContextConfiguration(classes = {TLSClient.class})
@Testcontainers
@IntegrationTest
@Slf4j
public class TLSClientTest {

  static final Network _docker_network = Network.newNetwork();

  @Container
  static final PostgreSQLContainer _database_container =
      new PostgreSQLContainer<>("postgres:11.5")
          .withDatabaseName("fc")
          .withUsername("fc")
          .withPassword("fc")
          .withNetworkAliases("db")
          .withNetwork(_docker_network);

  @Container
  static final GenericContainer _factcast_container =
      new GenericContainer<>("factcast/factcast:latest")
          .withExposedPorts(9443)
          .withFileSystemBind("./config", "/config/")
          .withEnv("spring.datasource.tomcat.max-wait", "20000")
          .withEnv("spring.datasource.tomcat.remove-abandoned-timeout", "360000")
          .withEnv("spring.datasource.tomcat.test-on-borrow", "true")
          .withEnv(
              "spring.datasource.tomcat.connectionProperties",
              "socketTimeout=20;connectTimeout=10;loginTimeout=10;")
          .withEnv("grpc.server.port", "9443")
          .withEnv("grpc.server.security.enabled", "true")
          .withEnv("grpc.server.security.certificateChain", "file:./config/localhost.crt")
          .withEnv("grpc.server.security.privateKey", "file:./config/localhost.key")
          .withEnv("factcast.security.enabled", "false")
          .withEnv("spring.datasource.url", "jdbc:postgresql://db/fc?user=fc&password=fc")
          .withNetwork(_docker_network)
          .dependsOn(_database_container)
          .withLogConsumer(new SysoutConsumer())
          .waitingFor(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));

  @BeforeAll
  public static void startContainers() throws InterruptedException {
    System.setProperty(
        "grpc.client.factstore.address",
        "static://"
            + _factcast_container.getHost()
            + ":"
            + _factcast_container.getMappedPort(9443));
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
