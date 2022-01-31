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

import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.Network;

public interface FactCastIntegrationTestExtension {

  Network _docker_network = Network.newNetwork();

  // returns true if successful, false if needed dependency is not yet available
  default boolean initialize(ExtensionContext context) {
    return true;
  }

  // in order to express, why the initialzation went wrong
  default String createUnableToInitializeMessage() {
    return "reason unknown";
  }

  default void beforeAll(ExtensionContext ctx) {}

  default void beforeEach(ExtensionContext ctx) {}

  default void afterEach(ExtensionContext ctx) {}

  default void afterAll(ExtensionContext ctx) {}
}
