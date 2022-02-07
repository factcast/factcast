package org.factcast.factus.dynamodb;

import static org.factcast.factus.dynamodb.DynamoConstants.*;

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
public class DynamoOperations {
  static final String LOCK_UNTIL = "lockUntil";
  static final String LOCKED_AT = "lockedAt";

  private static final String OBTAIN_LOCK_QUERY =
      "set " + LOCK_UNTIL + " = :lockUntil, " + LOCKED_AT + " = :lockedAt ";
  private static final String OBTAIN_LOCK_CONDITION =
      LOCK_UNTIL + " <= :lockedAt or attribute_not_exists(" + LOCK_UNTIL + ")";
  private static final int LOCK_EXPIRATION_SECONDS = 10;

  private final AmazonDynamoDB client;

  public DynamoOperations(@NonNull AmazonDynamoDB client) {
    this.client = client;
  }

  public void remove(@NonNull String lockId) {
    Map<String, AttributeValue> key = Collections.singletonMap("_id", DynamoConstants.attr(lockId));
    client.deleteItem(
        new DeleteItemRequest().withTableName(DynamoConstants.LOCK_TABLE).withKey(key));
  }

  @NonNull
  // maybe add param here to indicate short/longterm lock?
  public Optional<DynamoWriterToken> lock(@NonNull String lockId) {
    try {
      update(lockId, true);
      return Optional.of(new DynamoWriterToken(this, lockId));
    } catch (ConditionalCheckFailedException e) {
      // Condition failed. This means there was a lock with lockUntil > now.
      return Optional.empty();
    }
  }

  private void update(String lockId, boolean ifUnlocked) throws ConditionalCheckFailedException {
    Instant now = Instant.now();
    String nowIso = DynamoConstants.toIsoString(now);
    String lockUntilIso = toIsoString(now.plus(LOCK_EXPIRATION_SECONDS, ChronoUnit.SECONDS));

    Map<String, AttributeValue> key = Collections.singletonMap("_id", attr(lockId));

    Map<String, AttributeValue> attributeUpdates = new HashMap<>(2);
    attributeUpdates.put(":lockUntil", attr(lockUntilIso));
    attributeUpdates.put(":lockedAt", attr(nowIso));

    UpdateItemRequest request =
        new UpdateItemRequest()
            .withTableName(DynamoConstants.LOCK_TABLE)
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
