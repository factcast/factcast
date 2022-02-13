package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import lombok.NonNull;
import org.factcast.factus.projection.Projection;

public interface DynamoDBProjection extends Projection {
  @NonNull
  AmazonDynamoDB dynamoDB();
}
