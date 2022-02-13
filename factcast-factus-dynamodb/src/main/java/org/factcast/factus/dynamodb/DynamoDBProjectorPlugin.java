package org.factcast.factus.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.google.auto.service.AutoService;
import java.util.Collection;
import java.util.Collections;
import lombok.NonNull;
import org.factcast.factus.dynamodb.tx.DynamoDBTransactional;
import org.factcast.factus.dynamodb.tx.DynamoDBTransactionalLens;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;

@AutoService(ProjectorPlugin.class)
public class DynamoDBProjectorPlugin implements ProjectorPlugin {

  @Override
  public Collection<ProjectorLens> lensFor(@NonNull Projection p) {
    if (p instanceof DynamoDBProjection) {

      DynamoDBTransactional transactional = p.getClass().getAnnotation(DynamoDBTransactional.class);

      DynamoDBProjection projection = (DynamoDBProjection) p;
      AmazonDynamoDB client = projection.dynamoDB();

      if (transactional != null) {
        return Collections.singletonList(new DynamoDBTransactionalLens(projection, client));
      }
    }
    return Collections.emptyList();
  }
}
