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

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import java.util.Collection;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.dynamodb.tx.DynamoDBTransactionalLens;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamoDBProjectorPluginTest {

  @Mock private ProjectorPlugin projectorPlugin;
  @InjectMocks private DynamoDBProjectorPlugin underTest;

  @Nested
  class WhenLensingFor {
    @Mock private Projection p;

    @Test
    void findsTx() {
      assertThat(underTest.lensFor(new TX(mock(AmazonDynamoDB.class))).iterator().next())
          .isInstanceOf(DynamoDBTransactionalLens.class);
    }

    @Test
    void emptyOnNoAnnotation() {
      Collection<ProjectorLens> actual = underTest.lensFor(new None(mock(AmazonDynamoDB.class)));
      Assertions.assertThat(actual).isEmpty();
    }
  }
}

@ProjectionMetaData(serial = 1)
class TX extends ADynamoDBManagedProjection {
  public TX(@NonNull AmazonDynamoDB dynamoDB) {
    super(dynamoDB);
  }
}

@ProjectionMetaData(serial = 1)
class None extends AbstractDynamoDBManagedProjection {
  public None(@NonNull AmazonDynamoDB dynamoDB) {
    super(dynamoDB);
  }
}
