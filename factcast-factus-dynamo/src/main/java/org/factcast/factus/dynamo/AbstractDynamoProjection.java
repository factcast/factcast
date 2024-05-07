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
package org.factcast.factus.dynamo;

import com.amazonaws.services.dynamodbv2.AcquireLockOptions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClientOptions;
import com.amazonaws.services.dynamodbv2.LockItem;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

abstract class AbstractDynamoProjection
    implements DynamoProjection, FactStreamPositionAware, WriterTokenAware, Named {

  protected final DynamoDbEnhancedClient enhancedClient;
  private final AmazonDynamoDBLockClient lockClient;

  private final DynamoDbTable<DynamoProjectionState> stateTable;

  @Getter private final String projectionKey;
  private static final long LEASE_DURATION = 10L;

  protected AbstractDynamoProjection(
      @NonNull DynamoDbClient dynamoDb, String projectionTableName, String stateTableName) {
    this.enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDb).build();

    this.stateTable =
        enhancedClient.table(
            stateTableName, TableSchema.fromImmutableClass(DynamoProjectionState.class));
    this.projectionKey = projectionTableName;

    this.lockClient =
        new AmazonDynamoDBLockClient(
            AmazonDynamoDBLockClientOptions.builder(dynamoDb, stateTableName)
                .withLeaseDuration(LEASE_DURATION)
                .withTimeUnit(TimeUnit.SECONDS)
                .withHeartbeatPeriod(2L)
                .withCreateHeartbeatBackgroundThread(false)
                .build());
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    DynamoProjectionState state =
        stateTable.getItem(DynamoProjectionState.builder().key(projectionKey).build());
    return state != null ? FactStreamPosition.of(state.factStreamPosition(), state.serial()) : null;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@Nullable FactStreamPosition position) {
    stateTable.updateItem(
        UpdateItemEnhancedRequest.builder(DynamoProjectionState.class)
            .item(
                DynamoProjectionState.builder()
                    .key(projectionKey)
                    .factStreamPosition(position.factId())
                    .serial(position.serial())
                    .build())
            .build());

    stateTable.putItem(
        PutItemEnhancedRequest.builder(DynamoProjectionState.class)
            .item(
                DynamoProjectionState.builder()
                    .key(projectionKey)
                    .factStreamPosition(position.factId())
                    .serial(position.serial())
                    .build())
            .build());
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    try {
      String lockKey = projectionKey + "_lock";
      Optional<LockItem> lock =
          lockClient.tryAcquireLock(
              AcquireLockOptions.builder(lockKey)
                  // maxWait is applied on top of leaseDuration period.
                  .withAdditionalTimeToWaitForLock(
                      Math.max(maxWait.getSeconds() - LEASE_DURATION, 0))
                  .withTimeUnit(TimeUnit.SECONDS)
                  .build());
      if (lock.isPresent()) {
        return new DynamoWriterToken(lock.get());
      }
    } catch (InterruptedException e) {
      // assume lock unsuccessful
    }
    return null;
  }
}
