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
import org.factcast.example.client.dynamo.hello.events.UserChangedV1;
import org.factcast.example.client.dynamo.hello.events.UserCreatedV1;
import org.factcast.factus.Handler;
import org.factcast.factus.dynamo.AbstractDynamoManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@ProjectionMetaData(revision = 1)
@Slf4j
public class DynamoProjection extends AbstractDynamoManagedProjection {

  private final DynamoDbTable<UserSchema> userTable;

  public DynamoProjection(
      @NonNull DynamoDbClient dynamoDbClient,
      @NonNull String projectionTableName,
      @NonNull String stateTableName) {

    super(dynamoDbClient, projectionTableName, stateTableName);

    userTable = enhancedClient.table("users", TableSchema.fromImmutableClass(UserSchema.class));
  }

  @Handler
  void apply(UserCreatedV1 e) {
    userTable.putItem(
        PutItemEnhancedRequest.builder(UserSchema.class)
            .item(
                UserSchema.builder()
                    .displayName(e.lastName() + e.firstName())
                    .firstName(e.firstName())
                    .lastName(e.lastName())
                    .build())
            .build());
    log.info("UserCreated processed");
  }

  @Handler
  void apply(UserChangedV1 e) {
    //    UserSchema user = .getItem(Key.builder().partitionValue(e.firstName()).build());

    // Only the last name can be changed.

    userTable.updateItem(
        UpdateItemEnhancedRequest.builder(UserSchema.class)
            .item(
                UserSchema.builder()
                    .firstName(e.firstName())
                    .displayName(e.lastName() + e.firstName())
                    .lastName(e.lastName())
                    .build())
            .build());

    log.info("UserChanged processed");
  }
}
