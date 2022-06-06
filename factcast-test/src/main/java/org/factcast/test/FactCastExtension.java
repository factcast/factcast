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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
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

    ArrayList<org.factcast.test.FactCastIntegrationTestExtension> discovered =
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

  private static final OkHttpClient client = new OkHttpClient();

  @SneakyThrows
  public static void resetProxy() {

    String host = toxiProxy.getHost();
    int controlPort = toxiProxy.getControlPort();
    URI uri = new URI("http://" + host + ":" + controlPort + "/reset");

    Request request =
        new Builder()
            .url(uri.toURL())
            .method(
                "POST",
                RequestBody.create(
                    MediaType.get("application/json"), "{}".getBytes(StandardCharsets.UTF_8)))
            .build();
    client.newCall(request).execute();
  }

  @SneakyThrows
  public static void setProxyState(String name, boolean shouldBeOn) {

    String host = toxiProxy.getHost();
    int controlPort = toxiProxy.getControlPort();
    URI uri = new URI("http://" + host + ":" + controlPort + "/proxies");

    RequestBody body =
        RequestBody.create(
            MediaType.get("application/json"),
            ("{\"enabled\":" + shouldBeOn + "}").getBytes(StandardCharsets.UTF_8));
    Request request = new Request.Builder().url(uri.toURL()).method("POST", body).build();
    client.newCall(request).execute();
  }
}
