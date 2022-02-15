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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;

/** shamelessly stolen from shedlock */
class DynamoDBOperations {
  static final String LOCK_UNTIL = "lockUntil";
  static final String LOCKED_AT = "lockedAt";

  private static final String OBTAIN_LOCK_QUERY =
      "set " + LOCK_UNTIL + " = :lockUntil, " + LOCKED_AT + " = :lockedAt ";
  private static final String OBTAIN_LOCK_CONDITION =
      LOCK_UNTIL + " <= :lockedAt or attribute_not_exists(" + LOCK_UNTIL + ")";
  // TODO
  public static final long LOCK_EXPIRATION = DynamoDBConstants.DEFAULT_TIMEOUT * 2;

  private final AmazonDynamoDB client;

  public DynamoDBOperations(@NonNull AmazonDynamoDB client) {
    this.client = client;
  }

  public void unLock(@NonNull String lockId) {
    Map<String, AttributeValue> key = Collections.singletonMap("_id", DynamoDBUtil.attrS(lockId));
    client.deleteItem(
        new DeleteItemRequest().withTableName(DynamoDBConstants.LOCK_TABLE).withKey(key));
  }

  @NonNull
  // maybe add param here to indicate short/longterm lock?
  public Optional<DynamoDBWriterToken> lock(@NonNull String lockId) {
    try {
      update(lockId, true);
      return Optional.of(new DynamoDBWriterToken(this, lockId));
    } catch (ConditionalCheckFailedException e) {
      // Condition failed. This means there was a lock with lockUntil > now.
      return Optional.empty();
    }
  }

  private void update(String lockId, boolean ifUnlocked) throws ConditionalCheckFailedException {
    Instant now = Instant.now();
    String nowIso = DynamoDBUtil.toIsoString(now);
    String lockUntilIso = DynamoDBUtil.toIsoString(now.plus(LOCK_EXPIRATION, ChronoUnit.MILLIS));

    Map<String, AttributeValue> key = Collections.singletonMap("_id", DynamoDBUtil.attrS(lockId));

    Map<String, AttributeValue> attributeUpdates = new HashMap<>(2);
    attributeUpdates.put(":lockUntil", DynamoDBUtil.attrS(lockUntilIso));
    attributeUpdates.put(":lockedAt", DynamoDBUtil.attrS(nowIso));

    var request =
        new UpdateItemRequest()
            .withTableName(DynamoDBConstants.LOCK_TABLE)
            .withKey(key)
            .withUpdateExpression(OBTAIN_LOCK_QUERY)
            .withExpressionAttributeValues(attributeUpdates)
            .withReturnValues(ReturnValue.UPDATED_NEW);

    if (ifUnlocked) request = request.withConditionExpression(OBTAIN_LOCK_CONDITION);

    // There are three possible situations:
    // 1. The lock document does not exist yet - it is inserted - we have the lock
    // 2. The lock document exists and lockUtil <= now - it is updated - we have the lock
    // 3. The lock document exists and lockUtil > now - ConditionalCheckFailedException is thrown
    client.updateItem(request);
  }

  public void refresh(@NonNull String lockId) {
    update(lockId, false);
  }
}
