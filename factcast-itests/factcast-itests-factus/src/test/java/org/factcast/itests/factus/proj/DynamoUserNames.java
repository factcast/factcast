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

import lombok.SneakyThrows;
import org.factcast.factus.Handler;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public interface DynamoUserNames {

  DynamoDbTable<DynamoUserNamesSchema> userNames();

  default int count() {
    return userNames().describeTable().table().itemCount().intValue();
  }

  default boolean contains(String name) {
    return userNames()
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

  @SneakyThrows
  @Handler
  default void apply(UserCreated created) {
    userNames()
        .putItem(
            PutItemEnhancedRequest.builder(DynamoUserNamesSchema.class)
                .item(
                    new DynamoUserNamesSchema()
                        .userId(created.aggregateId())
                        .userName(created.userName()))
                .build());
  }

  @SneakyThrows
  @Handler
  default void apply(UserDeleted deleted) {
    userNames()
        .deleteItem(
            DeleteItemEnhancedRequest.builder()
                .key(Key.builder().partitionValue(deleted.aggregateId().toString()).build())
                .build());
  }
}
