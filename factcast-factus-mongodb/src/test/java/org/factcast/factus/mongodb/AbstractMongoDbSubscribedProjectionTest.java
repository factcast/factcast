/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.factus.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.NonNull;
import org.bson.Document;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractMongoDbSubscribedProjectionTest {
  private static final String DB_NAME = "testDb";
  static final String SCOPED_NAME =
      "org.factcast.factus.mongodb.AbstractMongoDbSubscribedProjectionTest$TestProjection_1";

  @Mock MongoClient mongoClient;
  @Mock MongoDatabase mongoDatabase;
  @Mock MongoCollection<Document> stateTable;
  @Mock MongoCollection<Document> lockTable;

  TestProjection uut;

  @BeforeEach
  void setUp() {
    when(mongoClient.getDatabase(DB_NAME)).thenReturn(mongoDatabase);
    when(mongoDatabase.getCollection(AbstractMongoDbManagedProjection.STATE_COLLECTION))
        .thenReturn(stateTable);
    when(mongoDatabase.getCollection(AbstractMongoDbManagedProjection.LOCK_COLLECTION))
        .thenReturn(lockTable);
    uut = new TestProjection(mongoClient);
  }

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
      assertThatThrownBy(() -> new MissingAnnotationTestProjection(mongoClient))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class TestProjection extends AbstractMongoDbSubscribedProjection {

    public TestProjection(@NonNull MongoClient mongoClient) {
      super(mongoClient, DB_NAME);
    }
  }

  static class MissingAnnotationTestProjection extends AbstractMongoDbSubscribedProjection {

    public MissingAnnotationTestProjection(@NonNull MongoClient mongoClient) {
      super(mongoClient, DB_NAME);
    }
  }
}
