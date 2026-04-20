/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.factus.mongodb.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.mongodb.client.MongoDatabase;
import java.util.UUID;
import lombok.SneakyThrows;
import org.bson.Document;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.FactusConstants;
import org.factcast.factus.mongodb.MongoDbProjection;
import org.factcast.factus.mongodb.MongoDbWriterToken;
import org.factcast.factus.mongodb.MongoDbWriterTokenManager;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class AbstractMongoDbTxProjectionTest {

  private static final String SCOPED_NAME =
      "org.factcast.factus.mongodb.tx.AbstractMongoDbTxProjectionTest$TestProjection_1";

  @Mock MongoTransactionManager transactionManager;
  @Mock MongoTemplate template;
  @Mock MongoDbWriterTokenManager writerTokenManager;
  @Mock MongoDatabase db;

  private TestProjection uut;

  @BeforeEach
  void setUp() {
    uut = new TestProjection(transactionManager, template, writerTokenManager);
  }

  @Nested
  class WhenHandlingFactStreamPosition {

    private final UUID factId = UUID.randomUUID();
    private final long lastSerial = 3L;

    final ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);

    @Test
    void gettingFactStreamPosition() {
      when(template.findOne(any(), any()))
          .thenReturn(new AbstractMongoDbTxProjection.State(SCOPED_NAME, factId, lastSerial));

      final FactStreamPosition position = uut.factStreamPosition();

      verify(template).findOne(queryCaptor.capture(), eq(AbstractMongoDbTxProjection.State.class));
      assertThat(
              queryCaptor.getValue().getQueryObject().get(MongoDbProjection.PROJECTION_CLASS_FIELD))
          .isEqualTo(SCOPED_NAME);

      assertThat(position).isNotNull();
      assertThat(position.factId()).isEqualTo(factId);
      assertThat(position.serial()).isEqualTo(lastSerial);
    }

    @Test
    void returnsNullWhenNotSet() {
      when(template.findOne(any(), any())).thenReturn(null);

      assertThat(uut.factStreamPosition()).isNull();
    }

    @Test
    void settingFactStreamPosition() {
      final ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
      final FactStreamPosition position = FactStreamPosition.of(factId, lastSerial);

      uut.factStreamPosition(position);

      verify(template)
          .upsert(
              queryCaptor.capture(),
              updateCaptor.capture(),
              eq(AbstractMongoDbTxProjection.State.class));

      assertThat(
              queryCaptor.getValue().getQueryObject().get(MongoDbProjection.PROJECTION_CLASS_FIELD))
          .isEqualTo(SCOPED_NAME);

      final Document setDoc = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
      assertThat(setDoc.get(MongoDbProjection.LAST_FACT_ID_FIELD)).isEqualTo(factId);
      assertThat(setDoc.get(MongoDbProjection.LAST_FACT_SERIAL_FIELD)).isEqualTo(lastSerial);
    }

    @Test
    void doesNotUpsertWhenFactIdIsNull() {
      uut.factStreamPosition(FactStreamPosition.of(null, 0L));

      verify(template, never()).upsert(any(), any(), eq(AbstractMongoDbTxProjection.State.class));
    }
  }

  @Nested
  class WhenAcquiringWriteToken {

    @SneakyThrows
    @Test
    void acquiresTokenForever() {
      final MongoDbWriterToken token = mock(MongoDbWriterToken.class);
      when(writerTokenManager.acquireWriteToken(FactusConstants.FOREVER)).thenReturn(token);

      AutoCloseable wt = uut.acquireWriteToken();

      assertThat(wt).isEqualTo(token);
    }
  }

  @Nested
  class WhenGettingMongoDb {

    @Test
    void delegatesToTemplate() {
      MongoDatabase db = mock(MongoDatabase.class);
      when(template.getDb()).thenReturn(db);

      assertThat(uut.mongoDb()).isEqualTo(db);
    }
  }

  @Nested
  class MissingProjectionMetaDataAnnotation {

    @Test
    void throwsOnMissingAnnotation() {
      assertThatThrownBy(() -> new MissingAnnotationTestProjection(transactionManager, template))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class TestProjection extends AbstractMongoDbTxManagedProjection {

    TestProjection(MongoTransactionManager tm, MongoTemplate t, MongoDbWriterTokenManager mgr) {
      super(tm, t, mgr);
    }
  }

  static class MissingAnnotationTestProjection extends AbstractMongoDbTxManagedProjection {
    MissingAnnotationTestProjection(MongoTransactionManager tm, MongoTemplate t) {
      super(tm, t);
    }
  }
}
