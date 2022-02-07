package org.factcast.factus.dynamodb;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import java.util.HashMap;
import java.util.Map;
import lombok.NonNull;

/** shamelessly stolen from shedlock */
class DynamoOperations {
  private final AmazonDynamoDBClient client;
  private final DynamoDBMapper mapper;

  public DynamoOperations(@NonNull AmazonDynamoDBClient client) {
    this.client = client;
    mapper = new DynamoDBMapper(client);
  }

  public void removeLock(@NonNull String lockIdentifier) {
    DeleteItemRequest request = new DeleteItemRequest();
    request.setReturnConsumedCapacity(ReturnConsumedCapacity.NONE);
    request.setReturnValues(ReturnValue.NONE);

    Map<String, AttributeValue> keysMap = new HashMap<>();
    keysMap.put("id", new AttributeValue(lockIdentifier));
    request.setKey(keysMap);

    // FIXME retry on unexpected SC / exception ?
    DeleteItemResult result = client.deleteItem(request);
    // FIXME check sc
  }

  public void lock(@NonNull String lockIdentifier) {
    UpdateItemRequest request = new UpdateItemRequest();
    request.setTableName(DynamoConstants.LOCK_TABLE);
    request.setReturnConsumedCapacity(ReturnConsumedCapacity.NONE);
    request.setReturnValues(ReturnValue.NONE);

    Map<String, AttributeValue> keysMap = new HashMap<>();
    keysMap.put("id", new AttributeValue(lockIdentifier));
    request.setKey(keysMap);

    Map<String, AttributeValueUpdate> map = new HashMap<>();
    map.put(
        "expires",
        new AttributeValueUpdate(
            new AttributeValue().withN(String.valueOf(System.currentTimeMillis())), "PUT"));
    request.setAttributeUpdates(map);

    // TODO need to block & retry here if condition fails

    try {
      UpdateItemResult result = client.updateItem(request);
      // There are three possible situations:
      // 1. The lock document does not exist yet - it is inserted - we have the lock
      // 2. The lock document exists and lockUtil <= now - it is updated - we have the lock
      // 3. The lock document exists and lockUtil > now - ConditionalCheckFailedException is thrown
      int httpStatusCode = result.getSdkHttpMetadata().getHttpStatusCode();
      if (httpStatusCode / 100 != 2) {
        // not OK
        throw new IllegalStateException(
            "Lock refresh failed with SC:" + httpStatusCode + "\n" + result);
      }
    } catch (ConditionalCheckFailedException e) {
      // failed to aquire lock
    } catch (AmazonServiceException e) {
      throw new IllegalStateException("Lock refresh failed with Exception", e);
    }
  }

  public boolean retrieveLockState(@NonNull String lockIdentifier) {
    return true; // TODO
  }
}
