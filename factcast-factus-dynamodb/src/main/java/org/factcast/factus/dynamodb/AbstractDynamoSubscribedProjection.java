package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import lombok.NonNull;
import org.factcast.factus.projection.SubscribedProjection;

public abstract class AbstractDynamoSubscribedProjection extends AbstractDynamoProjection
    implements SubscribedProjection {
  public AbstractDynamoSubscribedProjection(@NonNull AmazonDynamoDBClient client) {
    super(client);
  }
}
