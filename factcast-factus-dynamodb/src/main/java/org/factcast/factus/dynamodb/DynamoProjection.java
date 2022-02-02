package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import lombok.NonNull;
import org.factcast.factus.projection.Projection;

public interface DynamoProjection extends Projection {
  @NonNull
  AmazonDynamoDBClient dynamoDB();
}
