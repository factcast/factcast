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
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class FactCastIntegrationTestExecutionListener implements TestExecutionListener {

  public static final Network _docker_network = Network.newNetwork();
  public static final String TOXIPROXY_NETWORK_ALIAS = "toxiproxy";

  private static List<FactCastIntegrationTestExtension> extensions = new LinkedList<>();
  private static List<FactCastIntegrationTestExtension> reverseExtensions;
  private static ToxiproxyContainer toxiProxy;
  private static ToxiproxyClient toxiClient;

  private static AtomicBoolean initialized = new AtomicBoolean(false);

  @Override
  public void prepareTestInstance(TestContext testContext) throws Exception {}

  @Override
  public void beforeTestExecution(TestContext testContext) throws Exception {}

  @Override
  public void beforeTestClass(TestContext testContext) throws Exception {

    if (!initialized.getAndSet(true)) initialize();

    for (FactCastIntegrationTestExtension e : extensions) {
      e.prepareContainers(testContext);
    }

    for (FactCastIntegrationTestExtension e : extensions) {
      e.beforeAll(testContext);
    }
  }

  @Override
  public void beforeTestMethod(TestContext testContext) throws Exception {

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
  public void afterTestMethod(TestContext testContext) throws SQLException {

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
    GenericContainer fc;
    PostgresqlProxy pgProxy;
    FactCastProxy fcProxy;
    String jdbcUrl;
  }

  private void initialize() {
    // proxy will be started just once and always be the same container.
    initializeProxy();

    ArrayList<org.factcast.test.FactCastIntegrationTestExtension> discovered =
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
                DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.4.0")
                    .asCompatibleSubstituteFor("shopify/toxiproxy"))
            .withNetwork(_docker_network)
            .withNetworkAliases(TOXIPROXY_NETWORK_ALIAS);
    toxiProxy.start();
    String host = toxiProxy.getHost();
    int controlPort = toxiProxy.getControlPort();
    toxiClient = new ToxiproxyClient(host, controlPort);
  }

  public static ToxiproxyContainer.ContainerProxy createProxy(
      GenericContainer<?> container, int port) {
    return toxiProxy.getProxy(container, port);
  }

  public static ToxiproxyClient client() {
    return toxiClient;
  }
}
