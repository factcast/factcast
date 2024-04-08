/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.example.client.dynamo.hello;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.dynamo.AbstractDynamoManagedProjection;
import org.factcast.factus.dynamo.tx.DynamoTransaction;
import org.factcast.factus.dynamo.tx.DynamoTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@ProjectionMetaData(revision = 1)
@Slf4j
@DynamoTransactional()
public class DynamoProjection extends AbstractDynamoManagedProjection {

  private final DynamoDbTable<UserCreated> userTable;

  public DynamoProjection(
      @NonNull DynamoDbClient dynamoDbClient,
      @NonNull String projectionTableName,
      @NonNull String stateTableName) {

    super(dynamoDbClient, projectionTableName, stateTableName);

    userTable = enhancedClient.table("users", TableSchema.fromImmutableClass(UserCreated.class));
  }

  //  @Handler
  //    void apply(UserCreatedV1 e) {
  //        userTable.putItem(
  //            new UserCreated()
  //                .displayName(e.lastName() + e.firstName())
  //                .firstName(e.firstName())
  //                .lastName(e.lastName())
  //                .salutation("Hi there")
  //                );
  //    }
  //
  @Handler
  void apply(UserCreatedV1 e, DynamoTransaction tx) {
    tx.addPutRequest(
        userTable,
        TransactPutItemEnhancedRequest.builder(UserCreated.class)
            .item(
                UserCreated.builder()
                    .displayName(e.lastName() + e.firstName())
                    .firstName(e.firstName())
                    .lastName(e.lastName())
                    .salutation("Hi there")
                    .build())
            .build());
    log.info("Event processed");
  }
}
