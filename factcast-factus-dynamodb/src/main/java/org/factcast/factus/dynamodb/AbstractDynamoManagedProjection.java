package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import lombok.NonNull;

public abstract class AbstractDynamoManagedProjection extends AbstractDynamoProjection
    implements DynamoManagedProjection {
  public AbstractDynamoManagedProjection(@NonNull AmazonDynamoDBClient client) {
    super(client);
  }
}
