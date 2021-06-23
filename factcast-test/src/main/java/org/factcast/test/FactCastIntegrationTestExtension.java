package org.factcast.test;

import java.util.Map;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
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
