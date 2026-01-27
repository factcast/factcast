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

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.factcast.core.snap.mongo.MongoDbSnapshotCache.*;
import static org.mockito.Mockito.*;

import com.mongodb.MongoGridFSException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.awaitility.Awaitility;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
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

  @Mock private MongoCollection<Document> filesCollection;
  @Mock private GridFSBucket gridFSBucket;

  private MongoDbSnapshotCache underTest;

  private final SnapshotIdentifier id = SnapshotIdentifier.of(TestAggregate.class, randomUUID());
  private final SnapshotSerializerId serId = SnapshotSerializerId.of("buh");

  @BeforeEach
  void setUp() {
    MongoDbSnapshotProperties props = new MongoDbSnapshotProperties();
    underTest = spy(new MongoDbSnapshotCache(gridFSBucket, filesCollection, props));
  }

  @Nested
  class WhenGettingSnapshot {
    @Test
    void happyCase() {

      String payload = "payload";
      byte[] bytes = payload.getBytes();
      GridFSDownloadStream downloadStream = mock(GridFSDownloadStream.class);
      GridFSFile gridFSFile = mock(GridFSFile.class);

      String fileId = underTest.getFileName(id);
      when(gridFSBucket.openDownloadStream(fileId)).thenReturn(downloadStream);
      when(downloadStream.getGridFSFile()).thenReturn(gridFSFile);

      when(gridFSFile.getLength()).thenReturn((long) bytes.length);
      doAnswer(
              i -> {
                byte[] b = i.getArgument(0);
                System.arraycopy(bytes, 0, b, 0, bytes.length);
                return null;
              })
          .when(downloadStream)
          .read(any(byte[].class));

      UUID lastFactId = UUID.randomUUID();

      when(gridFSFile.getMetadata())
          .thenReturn(
              new Document()
                  .append(SNAPSHOT_SERIALIZER_ID_FIELD, serId.name())
                  .append(LAST_FACT_ID_FIELD, lastFactId.toString()));

      doNothing().when(underTest).tryUpdateExpirationDateAsync(any());
      Optional<SnapshotData> found = underTest.find(id);

      assertThat(found).isPresent();
      assertThat(found.get().serializedProjection()).isEqualTo("payload".getBytes());
      assertThat(found.get().snapshotSerializerId()).isEqualTo(serId);
      assertThat(found.get().lastFactId()).isEqualTo(lastFactId);

      Awaitility.await().untilAsserted(() -> verify(gridFSBucket).openDownloadStream(eq(fileId)));
    }

    @Test
    void shouldReturnEmptyOptionalWhenNoSnapshotFound() {
      String fileId = underTest.getFileName(id);
      when(gridFSBucket.openDownloadStream(fileId))
          .thenThrow(new MongoGridFSException("No file found"));

      Optional<SnapshotData> found = underTest.find(id);

      assertThat(found).isEmpty();
    }
  }

  @Nested
  class WhenStoringSnapshot {

    @Captor private ArgumentCaptor<String> fileIdCaptor;
    @Captor private ArgumentCaptor<GridFSUploadOptions> options;

    @Test
    void happyCase() {
      final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
      Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

      when(gridFSBucket.uploadFromStream(anyString(), any(), any())).thenReturn(new ObjectId());

      doNothing().when(underTest).tryDeleteOlderVersionsAsync(anyString(), any());
      underTest.store(id, snap);

      verify(gridFSBucket).uploadFromStream(fileIdCaptor.capture(), any(), options.capture());
      String fileName = fileIdCaptor.getValue();
      GridFSUploadOptions optionsValue = options.getValue();

      assertThat(fileName).isEqualTo(underTest.getFileName(id));

      Document storedDocument = optionsValue.getMetadata();

      assertThat(storedDocument).isNotNull();
      assertThat(storedDocument.getString("projectionClass"))
          .isEqualTo(id.projectionClass().getName());
      assertThat(storedDocument.getString("aggregateId"))
          .isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);
      assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
      assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap.lastFactId().toString());
      assertThat(storedDocument.get("expireAt", Instant.class))
          .isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void overwriteExistingSnapshot() {
      final SnapshotData snap1 = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
      final SnapshotData snap2 = new SnapshotData("bar".getBytes(), serId, UUID.randomUUID());
      Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

      when(gridFSBucket.uploadFromStream(anyString(), any(), any())).thenReturn(new ObjectId());

      doNothing().when(underTest).tryDeleteOlderVersionsAsync(anyString(), any());
      underTest.store(id, snap1);
      underTest.store(id, snap2);

      verify(gridFSBucket, times(2))
          .uploadFromStream(fileIdCaptor.capture(), any(), options.capture());
      String fileName = fileIdCaptor.getValue();
      GridFSUploadOptions optionsValue = options.getValue();

      assertThat(fileName).isEqualTo(underTest.getFileName(id));

      Document storedDocument = optionsValue.getMetadata();

      assertThat(storedDocument).isNotNull();
      assertThat(storedDocument.getString("projectionClass"))
          .isEqualTo(id.projectionClass().getName());
      assertThat(storedDocument.getString("aggregateId"))
          .isEqualTo(id.aggregateId() != null ? id.aggregateId().toString() : null);
      assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
      assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap2.lastFactId().toString());
      assertThat(storedDocument.get("expireAt", Instant.class))
          .isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void storeSnapshotWithoutAggregateId() {
      SnapshotIdentifier idWithoutAggregate = SnapshotIdentifier.of(TestSnapshotProjection.class);
      final SnapshotData snap = new SnapshotData("foo".getBytes(), serId, UUID.randomUUID());
      Instant expectedExpireAt = Instant.now().plus(90, ChronoUnit.DAYS);

      when(gridFSBucket.uploadFromStream(anyString(), any(), any())).thenReturn(new ObjectId());

      doNothing().when(underTest).tryDeleteOlderVersionsAsync(anyString(), any());
      underTest.store(idWithoutAggregate, snap);

      verify(gridFSBucket).uploadFromStream(fileIdCaptor.capture(), any(), options.capture());
      String fileName = fileIdCaptor.getValue();
      GridFSUploadOptions optionsValue = options.getValue();

      assertThat(fileName).isEqualTo(underTest.getFileName(idWithoutAggregate));

      Document storedDocument = optionsValue.getMetadata();

      assertThat(storedDocument).isNotNull();
      assertThat(storedDocument.getString("projectionClass"))
          .isEqualTo(idWithoutAggregate.projectionClass().getName());
      assertThat(storedDocument.getString("aggregateId"))
          .isEqualTo(idWithoutAggregate.aggregateId() != null ? id.aggregateId().toString() : null);
      assertThat(storedDocument.getString("snapshotSerializerId")).isEqualTo(serId.name());
      assertThat(storedDocument.getString("lastFactId")).isEqualTo(snap.lastFactId().toString());
      assertThat(storedDocument.get("expireAt", Instant.class))
          .isCloseTo(expectedExpireAt, within(1, ChronoUnit.SECONDS));
    }
  }

  @Nested
  class WhenRemovingSnapshot {
    @Captor private ArgumentCaptor<Bson> queryCaptor;

    @Test
    void happyCase() {
      GridFSFindIterable iterable = mock(GridFSFindIterable.class);
      when(gridFSBucket.find(any(Bson.class))).thenReturn(iterable);
      GridFSFile file = mock(GridFSFile.class);
      BsonObjectId fileId = new BsonObjectId(ObjectId.get());
      when(file.getId()).thenReturn(fileId);

      doAnswer(
              i -> {
                Consumer<GridFSFile> consumer = i.getArgument(0);
                consumer.accept(file);
                return null;
              })
          .when(iterable)
          .forEach(any());

      underTest.remove(id);

      verify(gridFSBucket).find(queryCaptor.capture());
      verify(gridFSBucket).delete(fileId);
    }

    @Test
    void removeSnapshotWithoutAggregateId() {
      SnapshotIdentifier idWithoutAggregate = SnapshotIdentifier.of(TestSnapshotProjection.class);

      GridFSFindIterable iterable = mock(GridFSFindIterable.class);
      when(gridFSBucket.find(any(Bson.class))).thenReturn(iterable);
      GridFSFile file = mock(GridFSFile.class);
      BsonObjectId fileId = new BsonObjectId(ObjectId.get());
      when(file.getId()).thenReturn(fileId);

      doAnswer(
              i -> {
                Consumer<GridFSFile> consumer = i.getArgument(0);
                consumer.accept(file);
                return null;
              })
          .when(iterable)
          .forEach(any());

      underTest.remove(idWithoutAggregate);

      verify(gridFSBucket).find(queryCaptor.capture());
      verify(gridFSBucket).delete(fileId);
    }
  }

  @Nested
  class WhenCleaningOldSnapshots {
    @Captor private ArgumentCaptor<Bson> docCaptor;

    @Test
    void happyCase() {
      GridFSFindIterable iterable = mock(GridFSFindIterable.class);
      when(gridFSBucket.find(any(Bson.class))).thenReturn(iterable);
      MongoCursor<GridFSFile> files = mock(MongoCursor.class);
      when(iterable.iterator()).thenReturn(files);
      when(files.hasNext()).thenReturn(true, false);

      GridFSFile file = mock(GridFSFile.class);
      BsonObjectId fileId = new BsonObjectId(ObjectId.get());
      when(file.getId()).thenReturn(fileId);
      when(files.next()).thenReturn(file);

      underTest.cleanupOldSnapshots();

      verify(gridFSBucket).delete(fileId);
    }
  }

  @Nested
  class WhenDeletingOlderVersions {
    @Test
    void happyCase() {
      GridFSFindIterable iterable = mock(GridFSFindIterable.class);
      when(gridFSBucket.find(any(Bson.class))).thenReturn(iterable);
      GridFSFile file1 = mock(GridFSFile.class);
      BsonObjectId fileId1 = new BsonObjectId(ObjectId.get());
      when(file1.getId()).thenReturn(fileId1);
      GridFSFile file2 = mock(GridFSFile.class);
      BsonObjectId fileId2 = new BsonObjectId(ObjectId.get());
      when(file2.getId()).thenReturn(fileId2);

      doAnswer(
              i -> {
                Consumer<GridFSFile> consumer = i.getArgument(0);
                consumer.accept(file1);
                consumer.accept(file2);
                return null;
              })
          .when(iterable)
          .forEach(any());

      underTest.tryDeleteOlderVersionsAsync("filename", fileId1.getValue());

      Awaitility.await().untilAsserted(() -> verify(gridFSBucket,times(1)).delete(fileId2));
    }
  }

  @Nested
  class WhenUpdatingExpirationDate {
    @Captor private ArgumentCaptor<Bson> filterCaptor;

    @Test
    void happyCase() {
      underTest.tryUpdateExpirationDateAsync(id);

      Awaitility.await().untilAsserted(() -> {
          verify(filesCollection).updateMany(filterCaptor.capture(), any(Bson.class));
            Bson filter = filterCaptor.getValue();
            assertThat(filter.toBsonDocument().get("filename").asString().getValue()).isEqualTo(underTest.getFileName(id));
      });
    }
  }

  @ProjectionMetaData(revision = 1)
  public class TestSnapshotProjection implements SnapshotProjection {}

  @ProjectionMetaData(revision = 1)
  public class TestAggregate extends Aggregate {}
}
