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

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.toxi.FactCastProxy;
import org.factcast.test.toxi.PostgresqlProxy;
import org.factcast.test.toxi.ToxiProxySupplier;
import org.junit.jupiter.api.extension.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
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

  private ToxiProxySupplier getProxySupplierFor(
      Class<? extends ToxiProxySupplier> type, Containers currentContainers) {

    if (type == null || FactCastProxy.class.equals(type))
      return new FactCastProxy(currentContainers.proxy.getProxy(currentContainers.fc, FC_PORT));

    if (PostgresqlProxy.class.equals(type))
      return new PostgresqlProxy(currentContainers.proxy.getProxy(currentContainers.db, PG_PORT));

    throw new IllegalArgumentException("Unexpected ProxySupplier requested");
  }

  private Collection<Field> getToxiFields(Object testInstance) {
    Set<Field> s = new HashSet<>();
    collectFields(testInstance.getClass(), s);
    return s;
  }

  private Set<Field> collectFields(Class<?> aClass, Set<Field> s) {
    if (aClass == null || aClass == Object.class) return s;

    s.addAll(List.of(aClass.getDeclaredFields()));
    return collectFields(aClass.getSuperclass(), s);
  }

  private void startOrReuse(FactcastTestConfig.Config config) {
    Containers containers =
        executions.computeIfAbsent(
            config,
            key -> {
              String dbName = "db" + config.hashCode();
              String TOXIPROXY_NETWORK_ALIAS = "toxiproxy" + config.hashCode();

              ToxiproxyContainer toxiProxy =
                  new ToxiproxyContainer("shopify/toxiproxy:2.1.0")
                      .withNetwork(_docker_network)
                      .withNetworkAliases(TOXIPROXY_NETWORK_ALIAS);

              toxiProxy.start();

              PostgreSQLContainer db =
                  new PostgreSQLContainer<>("postgres:" + config.postgresVersion())
                      .withDatabaseName("fc")
                      .withUsername("fc")
                      .withPassword("fc")
                      .withNetworkAliases(dbName)
                      .withNetwork(_docker_network);
              db.start();

              ContainerProxy pgProxy = toxiProxy.getProxy(db, PG_PORT);

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
                              + TOXIPROXY_NETWORK_ALIAS
                              + ":"
                              + pgProxy.getOriginalProxyPort()
                              + "/fc?user=fc&password=fc")
                      .withNetwork(_docker_network)
                      .dependsOn(db)
                      .withLogConsumer(
                          new Slf4jLogConsumer(
                              LoggerFactory.getLogger(AbstractFactCastIntegrationTest.class)))
                      .waitingFor(
                          new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));
              fc.start();

              return new Containers(db, fc, toxiProxy);
            });

    ContainerProxy fcProxy = containers.proxy.getProxy(containers.fc, FC_PORT);
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
    FactcastTestConfig.Config config = discoverConfig(ctx);
    Containers containers = executions.get(config);

    // reset proxies
    reset(containers.proxy);

    // inject toxi into field
    ctx.getTestInstance()
        .ifPresent(
            t -> {
              getProxyFields(t)
                  .forEach(
                      proxyField -> {
                        proxyField.setAccessible(true);
                        try {
                          ToxiProxySupplier proxySupplier =
                              getProxySupplierFor(
                                  (Class<? extends ToxiProxySupplier>) proxyField.getType(),
                                  containers);
                          proxyField.set(t, proxySupplier);
                        } catch (IllegalAccessException e) {
                          throw new RuntimeException(e);
                        }
                      });
            });

    // erase postgres
    PostgreSQLContainer pg = containers.db;
    String url = pg.getJdbcUrl();
    Properties p = new Properties();
    p.put("user", pg.getUsername());
    p.put("password", pg.getPassword());

    log.trace("erasing postgres state in between tests for {}", url);

    try (var con = DriverManager.getConnection(url, p);
        var st = con.createStatement()) {
      st.execute(
          "DO $$ DECLARE\n"
              + "    r RECORD;\n"
              + "BEGIN\n"
              + "    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = current_schema()"
              + " AND (NOT ((tablename like 'databasechangelog%') OR (tablename like 'qrtz%') OR"
              + " (tablename = 'schedlock')))) LOOP\n"
              + "        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || '';\n"
              + "    END LOOP;\n"
              + "END $$;");
    }
  }

  @SneakyThrows
  private void reset(ToxiproxyContainer proxy) {
    HttpClient cl = HttpClient.newHttpClient();
    String host = proxy.getHost();
    int controlPort = proxy.getControlPort();
    cl.send(
            HttpRequest.newBuilder()
                .method("POST", BodyPublishers.noBody())
                .uri(new URI("http://" + host + ":" + controlPort + "/reset"))
                .build(),
            BodyHandlers.ofString())
        .statusCode();
  }

  @NonNull
  private Stream<Field> getProxyFields(Object t) {
    return getToxiFields(t).stream()
        .filter(f -> ToxiProxySupplier.class.isAssignableFrom(f.getType()));
  }

  @Value
  static class Containers {
    PostgreSQLContainer db;
    GenericContainer fc;
    ToxiproxyContainer proxy;
  }
}
