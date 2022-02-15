package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import lombok.NonNull;
import org.factcast.factus.dynamodb.tx.DynamoDBTransactional;
import org.factcast.factus.serializer.ProjectionMetaData;

@ProjectionMetaData(serial = 1)
@DynamoDBTransactional
public class ADynamoDBManagedProjection extends AbstractDynamoDBManagedProjection {
  public ADynamoDBManagedProjection(@NonNull AmazonDynamoDB dynamoDB) {
    super(dynamoDB);
  }
}
