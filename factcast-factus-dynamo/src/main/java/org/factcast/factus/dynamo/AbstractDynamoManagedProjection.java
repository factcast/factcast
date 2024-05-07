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
package org.factcast.factus.dynamo;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient;
import lombok.NonNull;
import org.factcast.factus.projection.ManagedProjection;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public abstract class AbstractDynamoManagedProjection extends AbstractDynamoProjection
    implements ManagedProjection {
  public AbstractDynamoManagedProjection(
      @NonNull DynamoDbClient dynamoDbClient, @NonNull String stateTableName) {
    super(dynamoDbClient, stateTableName);
  }

  public AbstractDynamoManagedProjection(
      @NonNull DynamoDbClient dynamoDb,
      @NonNull DynamoDbEnhancedClient enhancedClient,
      @NonNull AmazonDynamoDBLockClient lockClient,
      @NonNull String stateTableName) {
    super(dynamoDb, enhancedClient, lockClient, stateTableName);
  }
}
