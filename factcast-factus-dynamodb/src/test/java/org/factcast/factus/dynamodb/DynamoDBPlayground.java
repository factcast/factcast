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
package org.factcast.factus.dynamodb;

import static org.assertj.core.api.Assertions.*;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.waiters.WaiterParameters;
import java.util.Optional;
import lombok.SneakyThrows;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.dynamodb.DynamoDBTestUtil;
import org.junit.jupiter.api.*;

public class DynamoDBPlayground extends AbstractFactCastIntegrationTest {

  private static final String TABLE_NAME = "myProjection";

  @BeforeAll
  public static void setUp() {
    AmazonDynamoDB client = DynamoDBTestUtil.getClient();

    {
      client.createTable(
          new CreateTableRequest()
              .withTableName(TABLE_NAME)
              .withKeySchema(
                  new KeySchemaElement().withKeyType(KeyType.HASH).withAttributeName("_id"))
              .withBillingMode(BillingMode.PAY_PER_REQUEST)
              .withAttributeDefinitions(
                  new AttributeDefinition()
                      .withAttributeName("_id")
                      .withAttributeType(ScalarAttributeType.S)));

      var describeTable = new DescribeTableRequest().withTableName(TABLE_NAME);
      var waitParams = new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
      client.waiters().tableExists().run(waitParams);
    }
    {
      client.createTable(
          new CreateTableRequest()
              .withTableName(DynamoDBConstants.LOCK_TABLE)
              .withKeySchema(
                  new KeySchemaElement().withKeyType(KeyType.HASH).withAttributeName("_id"))
              .withBillingMode(BillingMode.PAY_PER_REQUEST)
              .withAttributeDefinitions(
                  new AttributeDefinition()
                      .withAttributeName("_id")
                      .withAttributeType(ScalarAttributeType.S)));

      var describeTable = new DescribeTableRequest().withTableName(DynamoDBConstants.LOCK_TABLE);
      var waitParams = new WaiterParameters<DescribeTableRequest>().withRequest(describeTable);
      client.waiters().tableExists().run(waitParams);
    }
  }

  @SneakyThrows
  @Test
  void theTestWithNoName() {
    AmazonDynamoDB client = DynamoDBTestUtil.getClient();

    Optional<DynamoDBWriterToken> lock1 = new DynamoDBOperations(client).lock("l1");
    assertThat(lock1).isNotEmpty();

    Optional<DynamoDBWriterToken> lock2 = new DynamoDBOperations(client).lock("l2");
    assertThat(lock2).isNotEmpty();

    Optional<DynamoDBWriterToken> lock1b = new DynamoDBOperations(client).lock("l1");
    assertThat(lock1b).isEmpty();

    lock1.get().close();
    // re-acquire
    lock1b = new DynamoDBOperations(client).lock("l1");
    assertThat(lock1b).isNotEmpty();

    var relock2 = new DynamoDBOperations(client).lock("l2");
    assertThat(relock2).isEmpty(); // not able to acquire

    lock2.get().kill();
    Thread.sleep(DynamoDBOperations.LOCK_EXPIRATION + 500);

    // if we'd recheck
    // assertThat(lock2.get().isValid()).isFalse();
    relock2 = new DynamoDBOperations(client).lock("l2");
    assertThat(relock2).isNotEmpty(); // should be able to reacquire after the old one expired
  }
}
