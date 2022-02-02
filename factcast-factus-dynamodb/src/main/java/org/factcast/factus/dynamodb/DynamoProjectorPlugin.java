package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import java.util.Collection;
import java.util.Collections;
import lombok.NonNull;
import org.factcast.factus.dynamodb.tx.DynamoTransactional;
import org.factcast.factus.dynamodb.tx.DynamoTransactionalLens;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;

public class DynamoProjectorPlugin implements ProjectorPlugin {

  @Override
  public Collection<ProjectorLens> lensFor(@NonNull Projection p) {
    if (p instanceof DynamoProjection) {

      DynamoTransactional transactional = p.getClass().getAnnotation(DynamoTransactional.class);

      DynamoProjection projection = (DynamoProjection) p;
      AmazonDynamoDBClient client = projection.dynamoDB();

      if (transactional != null) {
        return Collections.singletonList(new DynamoTransactionalLens(projection, client));
      }
    }
    return Collections.emptyList();
  }
}
