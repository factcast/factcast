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
package org.factcast.itests.factus.proj;

import java.util.Collections;
import java.util.HashMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.dynamo.AbstractDynamoSubscribedProjection;
import org.factcast.factus.dynamo.tx.DynamoTransaction;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

@Slf4j
@ProjectionMetaData(revision = 1)
public class TxDynamoSubscribedUserNames extends AbstractDynamoSubscribedProjection {

  private final DynamoDbTable<UserNamesDynamoSchema> userNames;

  public TxDynamoSubscribedUserNames(DynamoDbClient client) {
    super(client, "UserNames", "DynamoProjectionStateTracking");
    this.userNames =
        enhancedClient.table("UserNamesManaged", TableSchema.fromBean(UserNamesDynamoSchema.class));
  }

  public int count() {
    return userNames.describeTable().table().itemCount().intValue();
  }

  public boolean contains(String name) {
    return userNames
        .query(
            QueryEnhancedRequest.builder()
                .filterExpression(
                    Expression.builder()
                        .expression("userName = :name")
                        .putExpressionValue(":name", AttributeValue.fromS(name))
                        .build())
                .limit(1)
                .build())
        .iterator()
        .hasNext();
  }

  // ---- processing:
  @SneakyThrows
  @Handler
  protected void apply(UserCreated created, DynamoTransaction tx) {
    // TODO find way to utilize the enhanced client for easy mapping while keeping batch size check
    HashMap<String, AttributeValue> item = new HashMap<>();
    item.put("key", AttributeValue.fromS(created.aggregateId().toString()));
    item.put("userName", AttributeValue.fromS(created.userName()));
    tx.add(
        TransactWriteItem.builder()
            .put(Put.builder().tableName(userNames.tableName()).item(item).build())
            .build());
  }

  @SneakyThrows
  @Handler
  protected void apply(UserDeleted deleted, DynamoTransaction tx) {
    // TODO find way to utilize the enhanced client for easy mapping while keeping batch size check
    tx.add(
        TransactWriteItem.builder()
            .delete(
                Delete.builder()
                    .tableName(userNames.tableName())
                    .key(
                        Collections.singletonMap(
                            "key", AttributeValue.fromS(deleted.aggregateId().toString())))
                    .build())
            .build());
  }
}
