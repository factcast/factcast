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

import static org.assertj.core.api.Assertions.*;
import config.RedissonProjectionConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.proj.UserV1;
import org.factcast.itests.factus.proj.UserV2;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactCastExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

@ExtendWith(FactCastExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@EnableAutoConfiguration
@ContextConfiguration(classes = {Application.class, RedissonProjectionConfiguration.class})
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
public class FactusClientTestWithSchemaRegistry extends AbstractFactCastIntegrationTest {

  protected static final Network _docker_network = Network.newNetwork();

  private static Path folderForSchemas;
  private static String oldAddress;

  static {
    try {
      folderForSchemas = Files.createTempDirectory("test_schemas").toAbsolutePath();

      log.info("Created temporary schema directory: {}", folderForSchemas);

      File registry = new ClassPathResource("example-registry").getFile();

      log.info("Copying schema files into temporary schema directory: {}", registry);
      FileUtils.copyDirectory(registry, folderForSchemas.toFile());

    } catch (IOException e) {
      // this is unexpected but kind of fatal
      log.error("Error creating schema directory", e);
    }
  }

  @SneakyThrows
  @AfterAll
  public static void cleanup() {
    FileUtils.deleteQuietly(folderForSchemas.toFile());
  }

  @Container
  protected static final PostgreSQLContainer _postgres =
      new PostgreSQLContainer<>("postgres:11.4")
          .withDatabaseName("fc")
          .withUsername("fc")
          .withPassword("fc")
          .withNetworkAliases("db")
          .withNetwork(_docker_network);

  @Container
  protected static final GenericContainer _factcast =
      new GenericContainer<>("factcast/factcast:latest")
          .withExposedPorts(9090)
          .withFileSystemBind("./config", "/config/")
          .withEnv("grpc.server.port", "9090")
          .withEnv("factcast.security.enabled", "false")
          .withEnv("spring.datasource.url", "jdbc:postgresql://db/fc?user=fc&password=fc")
          .withFileSystemBind(folderForSchemas.toString(), "/schemata/")
          .withEnv("FACTCAST_STORE_PGSQL_SCHEMA_REGISTRY_URL", "file:///schemata")
          .withNetwork(_docker_network)
          .dependsOn(_postgres)
          .withLogConsumer(new Slf4jLogConsumer(log))
          .waitingFor(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));

  @SuppressWarnings("rawtypes")
  @BeforeAll
  public static void startContainers() throws InterruptedException {
    String address = "static://" + _factcast.getHost() + ":" + _factcast.getMappedPort(9090);
    oldAddress = System.getProperty("grpc.client.factstore.address");
    System.setProperty("grpc.client.factstore.address", address);
  }

  @AfterAll
  public static void stopContainers() throws InterruptedException {
    _factcast.stop();
    _postgres.stop();
    System.setProperty("grpc.client.factstore.address", oldAddress);
  }

  @Autowired Factus ec;

  @Test
  public void testVersions_upcast() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    ec.publish(new org.factcast.itests.factus.event.versioned.v1.UserCreated(aggId, "foo"));

    // ASSERT
    // this should work anyways:
    UserV1 userV1 = ec.fetch(UserV1.class, aggId);
    assertThat(userV1.userName()).isEqualTo("foo");

    UserV2 userV2 = ec.fetch(UserV2.class, aggId);
    assertThat(userV2.userName()).isEqualTo("foo");
    assertThat(userV2.salutation()).isEqualTo("NA");
  }

  @Test
  public void testVersions_downcast() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    ec.publish(new org.factcast.itests.factus.event.versioned.v2.UserCreated(aggId, "foo", "Mr"));

    // ASSERT
    // this should work anyways:
    UserV2 userV2 = ec.fetch(UserV2.class, aggId);
    assertThat(userV2.userName()).isEqualTo("foo");
    assertThat(userV2.salutation()).isEqualTo("Mr");

    UserV1 userV1 = ec.fetch(UserV1.class, aggId);
    assertThat(userV1.userName()).isEqualTo("foo");
  }
}
