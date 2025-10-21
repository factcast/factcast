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
package org.factcast.core.snap.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

import com.mongodb.client.*;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoDbSnapshotCacheTest {

  @Mock private MongoClient mongoClient;
  @Mock private MongoDatabase mongoDatabase;
  @Mock private MongoCollection<Document> collection;
  @Mock private MongoCollection<GridFSFile> gridFsCollection;
  @Mock private MongoCollection<BsonDocument> gridFsChunkCollection;
  @Mock private GridFSBucket gridFSBucket;
  @Mock private ClientSession session;

  private MongoDbSnapshotCache underTest;

  private final SnapshotIdentifier id = SnapshotIdentifier.of(SnapshotProjection.class);
  private final SnapshotSerializerId serId = SnapshotSerializerId.of("buh");

  @BeforeEach
  void setUp() {
    MongoDbSnapshotProperties props = new MongoDbSnapshotProperties();
    when(mongoClient.getDatabase("db")).thenReturn(mongoDatabase);
    when(mongoDatabase.getCollection("factus_snapshot")).thenReturn(collection);
    when(mongoDatabase.getCollection("fs.files", GridFSFile.class)).thenReturn(gridFsCollection);
    when(mongoDatabase.getCollection("fs.chunks", BsonDocument.class))
        .thenReturn(gridFsChunkCollection);

    MongoCollection<Object> innerCollection = mock(MongoCollection.class);
    when(innerCollection.withTimeout(anyLong(), any(TimeUnit.class))).thenReturn(innerCollection);
    when(innerCollection.withReadPreference(any())).thenReturn(innerCollection);
    FindIterable iterable = mock(FindIterable.class);
    when(innerCollection.find()).thenReturn(iterable);
    when(iterable.projection(any())).thenReturn(iterable);
    when(iterable.first()).thenReturn(null);

    when(gridFsCollection.withCodecRegistry(any())).thenReturn(gridFsCollection);
    when(gridFsCollection.withDocumentClass(any())).thenReturn(innerCollection);
    when(gridFsCollection.createIndex(any(Bson.class), any())).thenReturn("index");
    when(gridFsCollection.withTimeout(anyLong(), any(TimeUnit.class))).thenReturn(gridFsCollection);
    when(gridFsCollection.withReadPreference(any())).thenReturn(gridFsCollection);
    ListIndexesIterable listIndexesIterable = mock(ListIndexesIterable.class);
    when(gridFsCollection.listIndexes()).thenReturn(listIndexesIterable);
    doAnswer(
            i -> {
              List arg = i.getArgument(0);
              return arg;
            })
        .when(listIndexesIterable)
        .into(any());
    when(gridFsCollection.deleteOne(any())).thenReturn(mock(DeleteResult.class));

    when(gridFsChunkCollection.withCodecRegistry(any())).thenReturn(gridFsChunkCollection);
    when(gridFsChunkCollection.withTimeout(anyLong(), any(TimeUnit.class)))
        .thenReturn(gridFsChunkCollection);
    when(gridFsChunkCollection.withReadPreference(any())).thenReturn(gridFsChunkCollection);
    when(gridFsChunkCollection.listIndexes()).thenReturn(listIndexesIterable);

    underTest = spy(new MongoDbSnapshotCache(mongoClient, "db", props));

    underTest.gridFSBucket(gridFSBucket);

    lenient().when(mongoClient.startSession()).thenReturn(session);
    lenient()
        .doAnswer(
            i -> {
              TransactionBody<Boolean> argument = i.getArgument(0);
              argument.execute();
              return null;
            })
        .when(session)
        .withTransaction(any(), any());

    verify(collection, times(1)).createIndex(any(Bson.class), any());
    verify(collection, times(2)).createIndex(any(Bson.class));
  }

  @Nested
  class WhenGettingSnapshot {
    @Captor private ArgumentCaptor<Document> documentCaptor;

    @Test
    void happyCase() {
      FindIterable<Document> findIterable = mock(FindIterable.class);
      UUID lastFactId = UUID.randomUUID();
      ObjectId fileId = ObjectId.get();
      Document result =
          new Document()
              .append("projectionClass", id.projectionClass().getName())
              .append("aggregateId", id.aggregateId() != null ? id.aggregateId().toString() : null)
              .append("snapshotSerializerId", serId.name())
              .append("fileId", fileId)
              .append("lastFactId", lastFactId.toString());

      when(collection.find(documentCaptor.capture())).thenReturn(findIterable);
      when(findIterable.first()).thenReturn(result);

      doAnswer(
              i -> {
                OutputStream os = i.getArgument(1);
                os.write("foo".getBytes());
                return null;
              })
          .when(gridFSBucket)
          .downloadToStream(eq(fileId), any(OutputStream.class));

      Optional<SnapshotData> found = underTest.find(id);

      assertThat(found).isPresent();
      assertThat(found.get().serializedProjection()).isEqualTo("foo".getBytes());
      assertThat(found.get().snapshotSerializerId()).isEqualTo(serId);
      assertThat(found.get().lastFactId()).isEqualTo(lastFactId);

      Awaitility.await().untilAsserted(() ->
      verify(collection).updateOne(eq(documentCaptor.getValue()), any(Bson.class)));
    }

    @Test
    void shouldReturnEmptyOptionalWhenNoSnapshotFound() {
      FindIterable<Document> findIterable = mock(FindIterable.class);

      when(collection.find(documentCaptor.capture())).thenReturn(findIterable);
      when(findIterable.first()).thenReturn(null);

      Optional<SnapshotData> found = underTest.find(id);

      assertThat(found).isEmpty();
    }
  }

  @Nested
  class WhenStoringSnapshot {

    @Captor private ArgumentCaptor<Document> keyCaptor;
    @Captor private ArgumentCaptor<Document> documentCaptor;

    @Test
    void happyCase() {
      final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
      Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

      ObjectId fileId = ObjectId.get();
      when(gridFSBucket.uploadFromStream(eq(session), anyString(), any())).thenReturn(fileId);

      underTest.store(id, snap);

      verify(collection)
          .replaceOne(
              eq(session),
              keyCaptor.capture(),
              documentCaptor.capture(),
              any(ReplaceOptions.class));
      Document keyDocument = keyCaptor.getValue();
      Document storedDocument = documentCaptor.getValue();

      // Validate key document
      assertThat(keyDocument.getString("projectionClass"))
          .isEqualTo(id.projectionClass().getName());
      assertThat(keyDocument.getString("aggregateId"))
          .isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);

      // Validate stored document
      assertThat(storedDocument.getString("projectionClass"))
          .isEqualTo(id.projectionClass().getName());
      assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
      assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap.lastFactId().toString());
      assertThat(storedDocument.getObjectId("fileId")).isEqualTo(fileId);
      assertThat(storedDocument.get("expireAt", Instant.class))
          .isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void overwriteExistingSnapshot() {
      final SnapshotData snap1 = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
      final SnapshotData snap2 = new SnapshotData("bar".getBytes(), serId, UUID.randomUUID());
      Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

      ObjectId fileId = ObjectId.get();
      when(gridFSBucket.uploadFromStream(eq(session), anyString(), any())).thenReturn(fileId);

      underTest.store(id, snap1);
      underTest.store(id, snap2);

      verify(collection, times(2))
          .replaceOne(
              eq(session),
              keyCaptor.capture(),
              documentCaptor.capture(),
              any(ReplaceOptions.class));

      // Validate key document
      Document keyDocument = keyCaptor.getValue();
      assertThat(keyDocument.getString("projectionClass"))
          .isEqualTo(id.projectionClass().getName());
      assertThat(keyDocument.getString("aggregateId"))
          .isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);

      // Validate stored document
      Document storedDocument = documentCaptor.getValue();
      assertThat(storedDocument.getString("projectionClass"))
          .isEqualTo(id.projectionClass().getName());
      assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
      assertThat(storedDocument.getObjectId("fileId")).isEqualTo(fileId);
      assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap2.lastFactId().toString());
      assertThat(storedDocument.get("expireAt", Instant.class))
          .isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void storeSnapshotWithoutAggregateId() {
      SnapshotIdentifier idWithoutAggregate = SnapshotIdentifier.of(SnapshotProjection.class);
      final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
      Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

      ObjectId fileId = ObjectId.get();
      when(gridFSBucket.uploadFromStream(eq(session), anyString(), any())).thenReturn(fileId);

      underTest.store(idWithoutAggregate, snap);

      verify(collection)
          .replaceOne(
              eq(session),
              keyCaptor.capture(),
              documentCaptor.capture(),
              any(ReplaceOptions.class));

      Document keyDocument = keyCaptor.getValue();
      Document storedDocument = documentCaptor.getValue();

      // Validate key document
      assertThat(keyDocument.getString("projectionClass"))
          .isEqualTo(idWithoutAggregate.projectionClass().getName());
      assertThat(keyDocument.containsKey("aggregateId")).isFalse();

      // Validate stored document
      assertThat(storedDocument.getString("projectionClass"))
          .isEqualTo(idWithoutAggregate.projectionClass().getName());
      assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
      assertThat(storedDocument.getObjectId("fileId")).isEqualTo(fileId);
      assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap.lastFactId().toString());
      assertThat(storedDocument.get("expireAt", Instant.class))
          .isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
    }
  }

  @Nested
  class WhenRemovingSnapshot {

    @Captor private ArgumentCaptor<Document> keyCaptor;

    @Test
    void happyCase() {
      FindIterable<Document> iterable = mock(FindIterable.class);
      when(collection.find(any(Document.class))).thenReturn(iterable);
      Document mock = mock(Document.class);
      when(iterable.first()).thenReturn(mock);
      ObjectId fileId = ObjectId.get();
      when(mock.getObjectId("fileId")).thenReturn(fileId);

      underTest.remove(id);

      verify(gridFSBucket).delete(session, fileId);
      verify(collection).deleteOne(eq(session), keyCaptor.capture());
      Document capturedKeyDocument = keyCaptor.getValue();

      // Validate captured key document
      assertThat(capturedKeyDocument.getString("projectionClass"))
          .isEqualTo(id.projectionClass().getName());
      assertThat(capturedKeyDocument.getString("aggregateId"))
          .isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);
    }

    @Test
    void removeSnapshotWithoutAggregateId() {
      SnapshotIdentifier idWithoutAggregate = SnapshotIdentifier.of(SnapshotProjection.class);

      FindIterable<Document> iterable = mock(FindIterable.class);
      when(collection.find(any(Document.class))).thenReturn(iterable);
      Document mock = mock(Document.class);
      when(iterable.first()).thenReturn(mock);
      ObjectId fileId = ObjectId.get();
      when(mock.getObjectId("fileId")).thenReturn(fileId);

      underTest.remove(idWithoutAggregate);

      verify(gridFSBucket).delete(session, fileId);
      verify(collection).deleteOne(eq(session), keyCaptor.capture());
      Document capturedKeyDocument = keyCaptor.getValue();

      // Validate captured key document
      assertThat(capturedKeyDocument.getString("projectionClass"))
          .isEqualTo(idWithoutAggregate.projectionClass().getName());
      assertThat(capturedKeyDocument.containsKey("aggregateId")).isFalse();
    }
  }
}
