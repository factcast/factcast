/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
