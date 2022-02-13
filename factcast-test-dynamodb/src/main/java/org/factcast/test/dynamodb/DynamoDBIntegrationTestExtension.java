package org.factcast.test.dynamodb;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;
import org.factcast.test.FactCastIntegrationTestExtension;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;

@SuppressWarnings("rawtypes")
@Slf4j
@AutoService(FactCastIntegrationTestExtension.class)
public class DynamoDBIntegrationTestExtension implements FactCastIntegrationTestExtension {

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer().withServices(LocalStackContainer.Service.DYNAMODB);

  @Override
  public boolean initialize(ExtensionContext ctx) {
    startOrReuse();
    return true;
  }

  private void startOrReuse() {
    if (!localstack.isRunning()) localstack.start();
  }

  @Override
  public void beforeAll(ExtensionContext ctx) {
    startOrReuse();
    FactCastIntegrationTestExtension.super.beforeAll(ctx);
  }

  @Override
  public void beforeEach(ExtensionContext ctx) {
    log.trace(
        "erasing dynamoDB state in between tests for {}",
        localstack.getEndpointConfiguration(Service.DYNAMODB));

    final var client = DynamoDBTestUtil.getClient(localstack);
    // TODO wipe
    client.shutdown();
  }
}
