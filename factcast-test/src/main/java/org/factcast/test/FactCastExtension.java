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

import com.google.common.collect.Lists;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class FactCastExtension
    implements Extension,
        BeforeEachCallback,
        BeforeAllCallback,
        AfterEachCallback,
        AfterAllCallback {

  public static final Network _docker_network = Network.newNetwork();
  public static final String TOXIPROXY_NETWORK_ALIAS = "toxiproxy";

  private static boolean initialized = false;
  private static final List<FactCastIntegrationTestExtension> extensions = new LinkedList<>();
  private static List<FactCastIntegrationTestExtension> reverseExtensions;
  private static ToxiproxyContainer toxiProxy;

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    for (FactCastIntegrationTestExtension e : extensions) {
      e.beforeEach(context);
    }
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    for (FactCastIntegrationTestExtension e : reverseExtensions) {
      e.afterAll(context);
    }
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    FactCastExtension.resetProxy();
    for (FactCastIntegrationTestExtension e : reverseExtensions) {
      e.afterEach(context);
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    synchronized (extensions) {
      if (!initialized) {
        initialize(context);
        initialized = true;
      }
    }
    for (FactCastIntegrationTestExtension e : extensions) {
      e.beforeAll(context);
    }
  }

  private void initialize(ExtensionContext context) {

    initializeProxy();

    var discovered =
        Lists.newArrayList(ServiceLoader.load(FactCastIntegrationTestExtension.class).iterator());
    AtomicInteger count = new AtomicInteger(discovered.size());
    while (!discovered.isEmpty()) {

      for (FactCastIntegrationTestExtension e : discovered) {
        if (e.initialize(context)) {
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
  }

  public static ContainerProxy proxy(String host, int port) {
    return toxiProxy.getProxy(host, port);
  }

  public static ContainerProxy proxy(GenericContainer container, int port) {
    return toxiProxy.getProxy(container, port);
  }

  @SneakyThrows
  public static void resetProxy() {
    HttpClient cl = HttpClient.newHttpClient();
    String host = toxiProxy.getHost();
    int controlPort = toxiProxy.getControlPort();
    cl.send(
            HttpRequest.newBuilder()
                .method("POST", BodyPublishers.noBody())
                .uri(new URI("http://" + host + ":" + controlPort + "/reset"))
                .build(),
            BodyHandlers.ofString())
        .statusCode();
  }

  @SneakyThrows
  public static void setProxyState(String name, boolean shouldBeOn) {
    HttpClient cl = HttpClient.newHttpClient();
    String host = toxiProxy.getHost();
    int controlPort = toxiProxy.getControlPort();
    cl.send(
            HttpRequest.newBuilder()
                .method("POST", BodyPublishers.ofString("{\"enabled\":" + shouldBeOn + "}"))
                .uri(new URI("http://" + host + ":" + controlPort + "/proxies/" + name))
                .build(),
            BodyHandlers.ofString())
        .statusCode();
  }
}
