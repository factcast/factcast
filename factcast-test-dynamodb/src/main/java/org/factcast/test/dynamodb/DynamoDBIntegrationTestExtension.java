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
