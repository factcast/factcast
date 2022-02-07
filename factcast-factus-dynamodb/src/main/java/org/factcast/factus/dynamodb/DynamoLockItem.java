package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Setter;

@Setter
@DynamoDBTable(tableName = DynamoConstants.LOCK_TABLE)
public class DynamoLockItem {

  private String id;
  private long createdAt;
  private long lockedUntil;
  private String lockedBy;

  @DynamoDBHashKey(attributeName = "id")
  public String getId() {
    return id;
  }

  @DynamoDBAttribute(attributeName = "createdAt")
  public long getCreatedAt() {
    return createdAt;
  }

  @DynamoDBAttribute(attributeName = "lockedUntil")
  public long getLockedUntil() {
    return lockedUntil;
  }

  @DynamoDBAttribute(attributeName = "lockedBy")
  public String getLockedBy() {
    return lockedBy;
  }
}
