package org.factcast.test.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import com.google.auto.service.AutoService;
import java.util.List;
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
    wipe(client);
    client.shutdown();
  }

  private void wipe(AmazonDynamoDB client) {
    client
        .listTables()
        .getTableNames()
        .forEach(
            tableName -> {
              var table = client.describeTable(tableName).getTable();
              var keySchema = table.getKeySchema();

              // drop an recreate
              client.deleteTable(tableName);
              CreateTableRequest createReq = new CreateTableRequest();
              createReq.withTableName(tableName);
              createReq.withKeySchema(keySchema);
              createReq.withAttributeDefinitions(table.getAttributeDefinitions());
              List<GlobalSecondaryIndexDescription> globalSecondaryIndexes =
                  table.getGlobalSecondaryIndexes();
              if (globalSecondaryIndexes != null)
                createReq.withGlobalSecondaryIndexes(
                    globalSecondaryIndexes.toArray(GlobalSecondaryIndex[]::new));
              List<LocalSecondaryIndexDescription> localSecondaryIndexes =
                  table.getLocalSecondaryIndexes();
              if (localSecondaryIndexes != null)
                createReq.withLocalSecondaryIndexes(
                    localSecondaryIndexes.toArray(LocalSecondaryIndex[]::new));
              createReq.withBillingMode(BillingMode.PAY_PER_REQUEST);
              client.createTable(createReq);

              // wait for completion
              var describeTable = new DescribeTableRequest().withTableName(tableName);
              var waitParams =
                  new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
              client.waiters().tableExists().run(waitParams);
            });
  }
}
