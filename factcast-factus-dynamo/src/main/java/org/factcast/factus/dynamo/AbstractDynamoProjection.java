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
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.dynamo.tx.DynamoTransaction;
import org.factcast.factus.dynamo.tx.DynamoTransactional;
import org.factcast.factus.projection.FactStreamPositionAware;
import org.factcast.factus.projection.Named;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.projection.WriterTokenAware;
import org.factcast.factus.projection.tx.AbstractOpenTransactionAwareProjection;
import org.factcast.factus.projection.tx.TransactionAware;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

abstract class AbstractDynamoProjection
    extends AbstractOpenTransactionAwareProjection<DynamoTransaction>
    implements DynamoProjection,
        TransactionAware,
        FactStreamPositionAware,
        WriterTokenAware,
        Named {
  @Getter protected final DynamoDbClient dynamoDb;

  private static final String STATE_TABLE_NAME = "_subscribed_projection";

  private final AmazonDynamoDBLockClient lockClient;

  @Getter private final String projectionKey;
  private final Map<String, AttributeValue> dynamoKey;

  protected AbstractDynamoProjection(@NonNull DynamoDbClient dynamoDb) {
    this.dynamoDb = dynamoDb;

    this.projectionKey = getScopedName().asString() + "_state_tracking";
    this.dynamoKey =
        Collections.singletonMap("key", AttributeValue.builder().s(projectionKey).build());

    this.lockClient =
        new AmazonDynamoDBLockClient(
            AmazonDynamoDBLockClientOptions.builder(dynamoDb, STATE_TABLE_NAME)
                .withTimeUnit(TimeUnit.SECONDS)
                .withLeaseDuration(10L)
                .withHeartbeatPeriod(3L)
                .withCreateHeartbeatBackgroundThread(false)
                .build());
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    if (inTransaction()) {
      return runningTransaction().initialFactStreamPosition();
    } else {

      final GetItemResponse res =
          dynamoDb.getItem(
              GetItemRequest.builder()
                  .tableName(STATE_TABLE_NAME)
                  .key(dynamoKey)
                  .attributesToGet("factStreamPosition", "factStreamSerial")
                  .build());
      return FactStreamPosition.of(
          UUID.fromString(res.item().get("factStreamPosition").toString()),
          Long.parseLong(res.item().get("factStreamSerial").toString()));
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void factStreamPosition(@Nullable FactStreamPosition position) {
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(
        ":new_factStreamPosition", AttributeValue.fromS(position.factId().toString()));
    expressionAttributeValues.put(
        ":new_factStreamSerial", AttributeValue.fromS(String.valueOf(position.serial())));
    if (inTransaction()) {
      // TODO transaction write item + evetl. condition
      DynamoTransaction transaction = runningTransaction();
      expressionAttributeValues.put(
          ":expected_status",
          AttributeValue.fromS(transaction.initialFactStreamPosition().factId().toString()));

      transaction.add(
          TransactWriteItem.builder()
              .update(
                  Update.builder()
                      .tableName(STATE_TABLE_NAME)
                      .key(dynamoKey)
                      .updateExpression(
                          "SET factStreamPosition = :new_status, factStreamSerial = :new_factStreamSerial")
                      .expressionAttributeValues(expressionAttributeValues)
                      .conditionExpression("factStreamPosition = :expected_status")
                      .build())
              .build());
    } else {
      // TODO: there probably is a cleaner way of serializing this back and forth
      // e.g. using DynamoDbEnhancedClient
      // https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Programming.SDKs.Interfaces.Mapper.html

      dynamoDb.updateItem(
          UpdateItemRequest.builder()
              .tableName(STATE_TABLE_NAME)
              .key(dynamoKey)
              .updateExpression(
                  "SET factStreamPosition = :new_status, factStreamSerial = :new_factStreamSerial")
              .expressionAttributeValues(expressionAttributeValues)
              .build());
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    assertNoRunningTransaction();
    try {
      Optional<LockItem> lock =
          lockClient.tryAcquireLock(AcquireLockOptions.builder(projectionKey).build());
      if (lock.isPresent()) {
        return new DynamoWriterToken(lock.get());
      }
    } catch (InterruptedException e) {
      // assume lock unsuccessful
    }
    return null;
  }

  @Override
  protected @NonNull DynamoTransaction beginNewTransaction() {
    FactStreamPosition position = factStreamPosition();
    DynamoTransaction tx = new DynamoTransaction(position);
    return tx;
  }

  /**
   * DynamoDb does not support rollback. Instead transactions are atomic and will fail completely
   */
  @Override
  protected void rollback(@NonNull DynamoTransaction runningTransaction) {
    // No rollback option with DynamoDB Transactions, the whole transaction will fail or succeed.
    // The current transaction is set to null in the super class after calling this method
  }

  @Override
  protected void commit(@NonNull DynamoTransaction runningTransaction) {
    TransactWriteItemsRequest transaction = runningTransaction.buildTransactionRequest();
    dynamoDb.transactWriteItems(transaction);
  }

  @Override
  public final int maxBatchSizePerTransaction() {
    DynamoTransactional tx = getClass().getAnnotation(DynamoTransactional.class);
    if (tx == null || tx.bulkSize() < 1) {
      return DynamoTransactional.Defaults.defaultBulkSize;
    } else {
      if (tx.bulkSize() > DynamoTransactional.Defaults.maxBulkSize) {
        throw new IllegalArgumentException("bulkSize cannot be bigger than the maxBulkSize " + DynamoTransactional.Defaults.maxBulkSize);
      }
      return tx.bulkSize();
    }
  }
}
