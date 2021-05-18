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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class FactCastExtension
    implements Extension,
        BeforeEachCallback,
        BeforeAllCallback,
        AfterEachCallback,
        AfterAllCallback {

  private static boolean initialized = false;
  private static final List<FactCastIntegrationTestExtension> extensions = new LinkedList<>();
  private static final Map<String, GenericContainer<?>> containers = new ConcurrentHashMap<>();
  private static List<FactCastIntegrationTestExtension> reverseExtensions;

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
    for (FactCastIntegrationTestExtension e : reverseExtensions) {
      e.afterEach(context);
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    synchronized (extensions) {
      if (!initialized) {
        initialize();
        initialized = true;
      }
    }
    for (FactCastIntegrationTestExtension e : extensions) {
      e.beforeAll(context);
    }
  }

  private void initialize() {
    val discovered =
        Lists.newArrayList(ServiceLoader.load(FactCastIntegrationTestExtension.class).iterator());
    AtomicInteger count = new AtomicInteger(discovered.size());
    while (!discovered.isEmpty()) {

      for (FactCastIntegrationTestExtension e : discovered) {
        if (e.initialize(containers)) {
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
}
