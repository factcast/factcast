package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import lombok.NonNull;

public abstract class AbstractDynamoDBManagedProjection extends AbstractDynamoDBProjection
    implements DynamoDBManagedProjection {
  protected AbstractDynamoDBManagedProjection(@NonNull AmazonDynamoDB client) {
    super(client);
  }
}
