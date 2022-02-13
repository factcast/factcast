package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import lombok.NonNull;
import org.factcast.factus.projection.SubscribedProjection;

public abstract class AbstractDynamoDBSubscribedProjection extends AbstractDynamoDBProjection
    implements SubscribedProjection {
  protected AbstractDynamoDBSubscribedProjection(@NonNull AmazonDynamoDBClient client) {
    super(client);
  }
}
