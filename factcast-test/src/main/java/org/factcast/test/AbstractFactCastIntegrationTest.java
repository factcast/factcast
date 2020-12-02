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
package org.factcast.test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@ExtendWith({FactCastExtension.class, RedisExtension.class})
@Slf4j
public class AbstractFactCastIntegrationTest {

  protected static final Network _docker_network = Network.newNetwork();

  protected static final PostgreSQLContainer _postgres =
      new PostgreSQLContainer<>("postgres:11.5")
          .withDatabaseName("fc")
          .withUsername("fc")
          .withPassword("fc")
          .withNetworkAliases("db")
          .withNetwork(_docker_network);

  protected static final GenericContainer _factcast =
      new GenericContainer<>("factcast/factcast:latest")
          .withExposedPorts(9090)
          .withFileSystemBind("./config", "/config/")
          .withEnv("grpc_server_port", "9090")
          .withEnv("factcast_security_enabled", "false")
          .withEnv("factcast_grpc_bandwidth_disabled", "true")
          .withEnv("spring_datasource_url", "jdbc:postgresql://db/fc?user=fc&password=fc")
          .withNetwork(_docker_network)
          .dependsOn(_postgres)
          .withLogConsumer(new Slf4jLogConsumer(log))
          .waitingFor(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));

  @SuppressWarnings("rawtypes")
  protected static final GenericContainer _redis =
      new GenericContainer<>("redis:5.0.9-alpine").withExposedPorts(6379);

  static {
    init();
  }

  @SuppressWarnings("rawtypes")
  @SneakyThrows
  private static void init() {
    List<GenericContainer> infra = Arrays.asList(_redis, _postgres);
    infra.parallelStream().forEach(GenericContainer::start);
    _factcast.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  _factcast.stop();
                  // the rest can be shut down by ryuk
                }));
  }

  @BeforeAll
  public static void startContainers() throws InterruptedException {
    String address = "static://" + _factcast.getHost() + ":" + _factcast.getMappedPort(9090);
    System.setProperty("grpc.client.factstore.address", address);

    System.setProperty("spring.redis.host", _redis.getHost());
    System.setProperty("spring.redis.port", String.valueOf(_redis.getMappedPort(6379)));
  }
}
