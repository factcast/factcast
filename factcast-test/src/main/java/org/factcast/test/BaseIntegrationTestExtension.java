/*
 * Copyright © 2017-2022 factcast.org
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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.toxi.FactCastProxy;
import org.factcast.test.toxi.PostgresqlProxy;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

@Slf4j
public class BaseIntegrationTestExtension implements FactCastIntegrationTestExtension {
  private static final int FC_PORT = 9090;
  private static final int PG_PORT = 5432;
  private static final Map<
          FactcastTestConfig.Config, FactCastIntegrationTestExecutionListener.Containers>
      executions = new ConcurrentHashMap<>();

  @Override
  public void prepareContainers(TestContext ctx) {
    FactcastTestConfig.Config config = discoverConfig(ctx.getTestClass());
    startOrReuse(config);
  }

  @Override
  @SneakyThrows
  public void wipeExternalDataStore(TestContext ctx) {
    erasePostgres(ctx.getApplicationContext().getBean(DataSource.class));
  }

  @Override
  public void injectFields(TestContext testContext) {
    FactcastTestConfig.Config config = discoverConfig(testContext.getTestClass());
    Object t = testContext.getTestInstance();
    FactCastIntegrationTestExtension.inject(t, executions.get(config).pgProxy());
    FactCastIntegrationTestExtension.inject(t, executions.get(config).fcProxy());
  }

  private FactcastTestConfig.Config discoverConfig(Class<?> testClass) {
    return Optional.ofNullable(testClass)
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(FactcastTestConfig.class)))
        .map(FactcastTestConfig.Config::from)
        .orElse(FactcastTestConfig.Config.defaults());
  }

  public void startOrReuse(FactcastTestConfig.Config config) {
    FactCastIntegrationTestExecutionListener.Containers containers =
        executions.computeIfAbsent(
            config,
            key -> {
              String dbName = "db" + config.hashCode();

              PostgreSQLContainer<?> db =
                  new PostgreSQLContainer<>("postgres:" + config.postgresVersion())
                      .withDatabaseName("fc")
                      .withUsername("fc")
                      .withPassword(UUID.randomUUID().toString())
                      .withNetworkAliases(dbName)
                      .withNetwork(FactCastIntegrationTestExecutionListener._docker_network);
              db.start();
              ToxiproxyContainer.ContainerProxy pgProxy =
                  FactCastIntegrationTestExecutionListener.createProxy(db, PG_PORT);

              String jdbcUrl =
                  "jdbc:postgresql://"
                      + FactCastIntegrationTestExecutionListener.TOXIPROXY_NETWORK_ALIAS
                      + ":"
                      + pgProxy.getOriginalProxyPort()
                      + "/"
                      + db.getDatabaseName();
              GenericContainer<?> fc =
                  new GenericContainer<>("factcast/factcast:" + config.factcastVersion())
                      .withExposedPorts(FC_PORT)
                      .withFileSystemBind(config.configDir(), "/config/")
                      .withEnv("grpc_server_port", String.valueOf(FC_PORT))
                      .withEnv(
                          "factcast_security_enabled", String.valueOf(config.securityEnabled()))
                      .withEnv("factcast_grpc_bandwidth_disabled", "true")
                      .withEnv("factcast_store_integrationTestMode", "true")
                      .withEnv("spring_datasource_url", jdbcUrl)
                      .withEnv("spring_datasource_username", db.getUsername())
                      .withEnv("spring_datasource_password", db.getPassword())
                      .withNetwork(FactCastIntegrationTestExecutionListener._docker_network)
                      .dependsOn(db)
                      .withLogConsumer(
                          new Slf4jLogConsumer(
                              LoggerFactory.getLogger(AbstractFactCastIntegrationTest.class)))
                      .waitingFor(
                          new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));
              fc.start();
              ToxiproxyContainer.ContainerProxy fcProxy =
                  FactCastIntegrationTestExecutionListener.createProxy(fc, FC_PORT);

              return new FactCastIntegrationTestExecutionListener.Containers(
                  db,
                  fc,
                  new PostgresqlProxy(pgProxy, FactCastIntegrationTestExecutionListener.client()),
                  new FactCastProxy(fcProxy, FactCastIntegrationTestExecutionListener.client()),
                  jdbcUrl);
            });

    ToxiproxyContainer.ContainerProxy fcProxy = containers.fcProxy().get();
    String address = "static://" + fcProxy.getContainerIpAddress() + ":" + fcProxy.getProxyPort();
    System.setProperty("grpc.client.factstore.address", address);

    System.setProperty("spring.datasource.url", containers.db().getJdbcUrl());
    System.setProperty("spring.datasource.username", containers.db().getUsername());
    System.setProperty("spring.datasource.password", containers.db().getPassword());
  }

  private void erasePostgres(DataSource ds) throws SQLException {

    log.trace("erasing postgres state in between tests");

    try (Connection con = ds.getConnection();
        Statement st = con.createStatement()) {
      st.execute(
          "DO $$ DECLARE\n"
              + "    r RECORD;\n"
              + "BEGIN\n"
              + "    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = current_schema()"
              + " AND (NOT ((tablename like 'databasechangelog%') OR (tablename like 'qrtz%') OR"
              + " (tablename = 'schedlock')))) LOOP\n"
              + "        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY ';\n"
              + "    END LOOP;\n"
              + "END $$;");
    }
  }

  @Override
  public void afterAll(TestContext ctx) {

    Class<?> tc = ctx.getTestClass();
    if (Arrays.stream(tc.getDeclaredFields())
        .anyMatch(f -> PostgresqlProxy.class.equals(f.getType()))) {

      // there was a postgres proxy involved. Depending on what we did with it, the factcast
      // container might now have broken connections in its pool, so lets make sure to restart it.

      FactCastIntegrationTestExecutionListener.Containers containers =
          executions.get(discoverConfig(ctx.getTestClass()));
      GenericContainer<?> fc = containers.fc();
      log.debug(
          "Cleanup after execution of "
              + tc.getClass()
              + ": PgProxy was involved, restarting factcast.");
      fc.stop();
      fc.start();
    }

    FactCastIntegrationTestExtension.super.afterAll(ctx);
  }
}
