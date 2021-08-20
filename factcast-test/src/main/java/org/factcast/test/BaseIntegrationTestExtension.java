package org.factcast.test;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.extension.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

@SuppressWarnings("rawtypes")
@Slf4j
public class BaseIntegrationTestExtension implements FactCastIntegrationTestExtension {
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
                      .withNetwork(_docker_network);

              GenericContainer fc =
                  new GenericContainer<>("factcast/factcast:" + config.factcastVersion())
                      .withExposedPorts(9090)
                      .withFileSystemBind(config.configDir(), "/config/")
                      .withEnv("grpc_server_port", "9090")
                      .withEnv("factcast_security_enabled", "false")
                      .withEnv("factcast_grpc_bandwidth_disabled", "true")
                      .withEnv("factcast_store_integrationTestMode", "true")
                      .withEnv(
                          "spring_datasource_url",
                          "jdbc:postgresql://" + dbName + "/fc?user=fc&password=fc")
                      .withNetwork(_docker_network)
                      .dependsOn(db)
                      .withLogConsumer(
                          new Slf4jLogConsumer(
                              LoggerFactory.getLogger(AbstractFactCastIntegrationTest.class)))
                      .waitingFor(
                          new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));

              db.start();
              fc.start();

              return new Containers(db, fc);
            });

    String address =
        "static://" + containers.fc.getHost() + ":" + containers.fc.getMappedPort(9090);
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

    PostgreSQLContainer pg = containers.db;
    String url = pg.getJdbcUrl();
    Properties p = new Properties();
    p.put("user", pg.getUsername());
    p.put("password", pg.getPassword());

    log.trace("erasing postgres state in between tests for {}", url);

    try (val con = DriverManager.getConnection(url, p);
        val st = con.createStatement()) {
      st.execute(
          "DO $$ DECLARE\n"
              + "    r RECORD;\n"
              + "BEGIN\n"
              + "    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND "
              + "("
              + "NOT ((tablename like 'databasechangelog%') OR (tablename like 'qrtz%') OR (tablename = 'schedlock')))"
              + ") LOOP\n"
              + "        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || '';\n"
              + "    END LOOP;\n"
              + "END $$;");
    }
  }

  @Value
  static class Containers {
    PostgreSQLContainer db;
    GenericContainer fc;
  }
}
