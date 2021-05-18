package org.factcast.test;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import lombok.SneakyThrows;
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
  private PostgreSQLContainer _postgres;

  @SuppressWarnings("FieldCanBeLocal")
  private GenericContainer _factcast;

  @Override
  public boolean initialize(Map<String, GenericContainer<?>> containers) {

    _postgres =
        new PostgreSQLContainer<>("postgres:11.5")
            .withDatabaseName("fc")
            .withUsername("fc")
            .withPassword("fc")
            .withNetworkAliases("db")
            .withNetwork(_docker_network);

    _factcast =
        new GenericContainer<>("factcast/factcast:latest")
            .withExposedPorts(9090)
            .withFileSystemBind("./config", "/config/")
            .withEnv("grpc_server_port", "9090")
            .withEnv("factcast_security_enabled", "false")
            .withEnv("factcast_grpc_bandwidth_disabled", "true")
            .withEnv("spring_datasource_url", "jdbc:postgresql://db/fc?user=fc&password=fc")
            .withNetwork(_docker_network)
            .dependsOn(_postgres)
            .withLogConsumer(
                new Slf4jLogConsumer(
                    LoggerFactory.getLogger(AbstractFactCastIntegrationTest.class)))
            .waitingFor(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(180)));
    //

    _postgres.start();
    _factcast.start();

    containers.put("postgres", _postgres);
    containers.put("factcast", _factcast);

    String address = "static://" + _factcast.getHost() + ":" + _factcast.getMappedPort(9090);
    System.setProperty("grpc.client.factstore.address", address);

    return true;
  }

  @SneakyThrows
  @Override
  public void beforeEach(ExtensionContext ctx) {
    val pg = _postgres;
    val url = pg.getJdbcUrl();
    Properties p = new Properties();
    p.put("user", pg.getUsername());
    p.put("password", pg.getPassword());

    log.trace("erasing postgres state in between tests for {}", url);

    try (val con = DriverManager.getConnection(url, p);
        val st = con.createStatement()) {
      st.execute("TRUNCATE fact");
      st.execute("TRUNCATE tokenstore");
      st.execute("TRUNCATE transformationcache");
      st.execute("TRUNCATE snapshot_cache");
    }
  }
}
