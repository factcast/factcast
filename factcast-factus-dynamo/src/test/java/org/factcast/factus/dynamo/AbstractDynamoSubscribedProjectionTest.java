/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.factus.dynamo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.NonNull;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@ExtendWith(MockitoExtension.class)
class AbstractDynamoSubscribedProjectionTest {
  @Mock DynamoDbClient dynamoDb;

  @InjectMocks TestProjection uut;

  static final String SCOPED_NAME =
      "org.factcast.factus.dynamo.AbstractDynamoSubscribedProjectionTest$TestProjection_1";

  @Nested
  class WhenInspectingClass {
    @Test
    void getProjectionKey() {
      assertThat(uut.projectionKey()).isEqualTo(SCOPED_NAME);
    }
  }

  @Nested
  class MissingProjectionMetaDataAnnotation {

    @Test
    void happyPath() {
      assertThatThrownBy(() -> new MissingAnnotationTestProjection(dynamoDb))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class TestProjection extends AbstractDynamoSubscribedProjection {
    public TestProjection(@NonNull DynamoDbClient dynamoDb) {
      super(dynamoDb, "stateTable");
    }
  }

  static class MissingAnnotationTestProjection extends AbstractDynamoSubscribedProjection {

    public MissingAnnotationTestProjection(@NonNull DynamoDbClient dynamoDb) {
      super(dynamoDb, "stateTable");
    }
  }
}
