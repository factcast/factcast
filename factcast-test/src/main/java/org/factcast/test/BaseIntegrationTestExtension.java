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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.toxi.FactCastProxy;
import org.factcast.test.toxi.PostgresqlProxy;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

@SuppressWarnings("rawtypes")
@Slf4j
public class BaseIntegrationTestExtension implements FactCastIntegrationTestExtension {
  private static final int FC_PORT = 9090;
  private static final int PG_PORT = 5432;
  private final Map<FactcastTestConfig.Config, Containers> executions = new ConcurrentHashMap<>();

  @Override
  public boolean initialize(ExtensionContext ctx) {
    FactcastTestConfig.Config config = discoverConfig(ctx);

    startOrReuse(config);

    return true;
  }

  private void startOrReuse(FactcastTestConfig.Config config) {
    Containers containers =
        executions.computeIfAbsent(
            config,
            key -> {
              String dbName = "db" + config.hashCode();

              PostgreSQLContainer db =
                  new PostgreSQLContainer<>("postgres:" + config.postgresVersion())
                      .withDatabaseName("fc")
                      .withUsername("fc")
                      .withPassword("fc")
                      .withNetworkAliases(dbName)
                      .withNetwork(FactCastExtension._docker_network);
              db.start();
              ContainerProxy pgProxy = FactCastExtension.proxy(db, PG_PORT);

              GenericContainer fc =
                  new GenericContainer<>("factcast/factcast:" + config.factcastVersion())
                      .withExposedPorts(FC_PORT)
                      .withFileSystemBind(config.configDir(), "/config/")
                      .withEnv("grpc_server_port", String.valueOf(FC_PORT))
                      .withEnv("factcast_security_enabled", "false")
                      .withEnv("factcast_grpc_bandwidth_disabled", "true")
                      .withEnv("factcast_store_integrationTestMode", "true")
                      .withEnv(
                          "spring_datasource_url",
                          "jdbc:postgresql://"
                              + FactCastExtension.TOXIPROXY_NETWORK_ALIAS
                              + ":"
                              + pgProxy.getOriginalProxyPort()
                              + "/fc?user=fc&password=fc")
                      .withNetwork(FactCastExtension._docker_network)
                      .dependsOn(db)
                      .withLogConsumer(
                          new Slf4jLogConsumer(
                              LoggerFactory.getLogger(AbstractFactCastIntegrationTest.class)))
                      .waitingFor(
                          new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));
              fc.start();
              ContainerProxy fcProxy = FactCastExtension.proxy(fc, FC_PORT);

              return new Containers(
                  db, fc, new PostgresqlProxy(pgProxy), new FactCastProxy(fcProxy));
            });

    ContainerProxy fcProxy = containers.fcProxy.get();
    String address = "static://" + fcProxy.getContainerIpAddress() + ":" + fcProxy.getProxyPort();
    System.setProperty("grpc.client.factstore.address", address);
  }

  @Override
  public void beforeAll(ExtensionContext ctx) {
    FactcastTestConfig.Config config = discoverConfig(ctx);
    startOrReuse(config);

    FactCastIntegrationTestExtension.super.beforeAll(ctx);
  }

  private FactcastTestConfig.Config discoverConfig(ExtensionContext ctx) {
    return ctx.getTestClass()
        .flatMap(x -> Optional.ofNullable(x.getAnnotation(FactcastTestConfig.class)))
        .map(FactcastTestConfig.Config::from)
        .orElse(FactcastTestConfig.Config.defaults());
  }

  @SneakyThrows
  @Override
  public void beforeEach(ExtensionContext ctx) {
    FactCastIntegrationTestExtension.super.beforeEach(ctx);

    FactcastTestConfig.Config config = discoverConfig(ctx);
    Containers containers = executions.get(config);

    ctx.getTestInstance()
        .ifPresent(
            t -> {
              FactCastIntegrationTestExtension.inject(t, containers.pgProxy);
              FactCastIntegrationTestExtension.inject(t, containers.fcProxy);
            });
    erasePostgres(containers);
  }

  @Override
  @SneakyThrows
  public void afterEach(ExtensionContext ctx) {
    erasePostgres(executions.get(discoverConfig(ctx)));
    FactCastIntegrationTestExtension.super.afterEach(ctx);
  }

  private void erasePostgres(Containers containers) throws SQLException {
    PostgreSQLContainer pg = containers.db;
    String url = pg.getJdbcUrl();
    Properties p = new Properties();
    p.put("user", pg.getUsername());
    p.put("password", pg.getPassword());

    log.trace("erasing postgres state in between tests for {}", url);

    try (Connection con = DriverManager.getConnection(url, p);
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

  @Value
  static class Containers {
    PostgreSQLContainer db;
    GenericContainer fc;
    PostgresqlProxy pgProxy;
    FactCastProxy fcProxy;
  }
}
