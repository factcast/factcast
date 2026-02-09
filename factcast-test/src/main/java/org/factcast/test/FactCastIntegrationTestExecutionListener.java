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

import com.google.common.collect.Lists;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.toxi.FactCastProxy;
import org.factcast.test.toxi.PostgresqlProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class FactCastIntegrationTestExecutionListener implements TestExecutionListener {

  public static final Network _docker_network = Network.newNetwork();
  public static final String TOXIPROXY_NETWORK_ALIAS = "toxiproxy";

  // Testcontainers docs: toxiproxy reserves 31 ports starting at 8666
  private static final int BASE_PROXY_PORT = 8666;
  private static final AtomicInteger NEXT = new AtomicInteger(0);

  private static List<FactCastIntegrationTestExtension> extensions = new LinkedList<>();
  private static List<FactCastIntegrationTestExtension> reverseExtensions;
  private static ToxiproxyContainer toxiProxy;
  private static ToxiproxyClient toxiClient;

  private static AtomicBoolean initialized = new AtomicBoolean(false);

  @Override
  public void beforeTestClass(@NonNull TestContext testContext) throws Exception {

    if (!initialized.getAndSet(true)) {
      initialize();
    }

    for (FactCastIntegrationTestExtension e : extensions) {
      e.prepareContainers(testContext);
    }

    for (FactCastIntegrationTestExtension e : extensions) {
      e.beforeAll(testContext);
    }
  }

  @Override
  public void beforeTestMethod(@NonNull TestContext testContext) throws Exception {

    toxiClient.reset();

    for (FactCastIntegrationTestExtension e : extensions) {
      e.wipeExternalDataStore(testContext);
    }

    for (FactCastIntegrationTestExtension e : extensions) {
      e.injectFields(testContext);
    }
    for (FactCastIntegrationTestExtension e : extensions) {
      e.beforeEach(testContext);
    }
  }

  @SneakyThrows
  @Override
  public void afterTestMethod(@NonNull TestContext testContext) throws SQLException {

    for (FactCastIntegrationTestExtension e : reverseExtensions) {
      e.afterEach(testContext);
    }
  }

  @Override
  public void afterTestClass(TestContext testContext) throws Exception {
    testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
    testContext.setAttribute(
        DependencyInjectionTestExecutionListener.REINJECT_DEPENDENCIES_ATTRIBUTE, Boolean.TRUE);

    for (FactCastIntegrationTestExtension e : reverseExtensions) {
      e.afterAll(testContext);
    }
  }

  @Override
  public void afterTestExecution(TestContext testContext) throws Exception {

    toxiClient.reset();

    for (FactCastIntegrationTestExtension e : extensions) {
      e.wipeExternalDataStore(testContext);
    }
  }

  @Value
  static class Containers {
    PostgreSQLContainer db;
    GenericContainer<?> fc;
    PostgresqlProxy pgProxy;
    FactCastProxy fcProxy;
    String jdbcUrl;
  }

  private static void initialize() {
    // proxy will be started just once and always be the same container.
    initializeProxy();

    ArrayList<FactCastIntegrationTestExtension> discovered =
        Lists.newArrayList(ServiceLoader.load(FactCastIntegrationTestExtension.class).iterator());
    AtomicInteger count = new AtomicInteger(discovered.size());
    while (!discovered.isEmpty()) {

      for (FactCastIntegrationTestExtension e : discovered) {
        if (e.initialize()) {
          extensions.add(e);
        }
      }

      discovered.removeAll(extensions);
      if (discovered.size() == count.getAndSet(discovered.size())) {
        // fail

        throw new IllegalStateException(
            "Failed to initialize extensions:\n"
                + discovered.stream()
                    .map(f -> " " + f.getClass() + ": " + f.createUnableToInitializeMessage())
                    .collect(Collectors.joining(",\n")));
      }
    }

    reverseExtensions = Lists.newArrayList(extensions);
    Collections.reverse(reverseExtensions);
  }

  private static void initializeProxy() {
    toxiProxy =
        new ToxiproxyContainer(
                DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0")
                    .asCompatibleSubstituteFor("shopify/toxiproxy"))
            .withNetwork(_docker_network)
            .withNetworkAliases(TOXIPROXY_NETWORK_ALIAS);
    toxiProxy.start();
    String host = toxiProxy.getHost();
    int controlPort = toxiProxy.getControlPort();
    toxiClient = new ToxiproxyClient(host, controlPort);
  }

  @SneakyThrows
  public static ProxiedEndpoint createProxy(String proxyName, GenericContainer<?> container, int port) {
    String toxiProxyHost = toxiProxy.getHost();
    ToxiproxyClient client =
        new ToxiproxyClient(toxiProxyHost, toxiProxy.getControlPort());

    String alias = container.getNetworkAliases().stream().findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Target container must have a network alias: .withNetworkAliases(\"some-name\")"));

    int listenPort = BASE_PROXY_PORT + NEXT.getAndIncrement();

    Proxy proxy = client.createProxy(
        proxyName,                  // arbitrary name
        "0.0.0.0:" + listenPort,     // toxiproxy listens here (inside toxiproxy container)
        alias + ":" + port     // target address (inside docker network)
    );

    return new ProxiedEndpoint(proxy, toxiProxyHost, toxiProxy.getMappedPort(listenPort));
  }

  public record ProxiedEndpoint(Proxy proxy, String host, int port) {}

  public static ToxiproxyClient client() {
    return toxiClient;
  }
}
