/*
 * Copyright © 2017-2020 factcast.org
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

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.MongoGridFSException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.factcast.factus.projection.ScopedName;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;

@Slf4j
public class MongoDbSnapshotCache implements SnapshotCache {
  public static final String PROJECTION_CLASS_FIELD = "projectionClass";
  public static final String AGGREGATE_ID_FIELD = "aggregateId";
  public static final String SNAPSHOT_SERIALIZER_ID_FIELD = "snapshotSerializerId";
  public static final String LAST_FACT_ID_FIELD = "lastFactId";
  public static final String EXPIRE_AT_FIELD = "expireAt";
  public static final String METADATA_EXPIRE_AT_FIELD = "metadata." + EXPIRE_AT_FIELD;
  public static final String FILENAME_FIELD = "filename";

  private static final ScheduledExecutorService CLEANUP_SCHEDULER =
      Executors.newScheduledThreadPool(1);

  private final MongoDbSnapshotProperties properties;
  private final MongoCollection<Document> filesCollection;

  private final GridFSBucket gridFSBucket;

  public MongoDbSnapshotCache(
      @NonNull MongoClient mongoClient,
      @NotNull String databaseName,
      @NonNull MongoDbSnapshotProperties properties) {
    this.properties = properties;
    MongoDatabase database = mongoClient.getDatabase(databaseName);

    gridFSBucket =
        GridFSBuckets.create(database, "factus_snapshots").withWriteConcern(WriteConcern.MAJORITY);

    // Create index for expiration date
    filesCollection = database.getCollection("factus_snapshots.files");
    filesCollection.createIndex(Indexes.ascending(METADATA_EXPIRE_AT_FIELD));

    // Schedule cleanup task
    scheduleCleanupTask();
  }

  @VisibleForTesting
  MongoDbSnapshotCache(
      GridFSBucket gridFSBucket,
      MongoCollection<Document> filesCollection,
      @NonNull MongoDbSnapshotProperties properties) {
    this.gridFSBucket = gridFSBucket;
    this.filesCollection = filesCollection;
    this.properties = properties;

    scheduleCleanupTask();
  }

  @Override
  @SneakyThrows
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    String fileName = getFileName(id);

    try (GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(fileName)) {
      GridFSFile gridFSFile = downloadStream.getGridFSFile();
      int fileLength = (int) gridFSFile.getLength();
      byte[] bytesToWriteTo = new byte[fileLength];
      int readBytes = downloadStream.read(bytesToWriteTo);

      if (readBytes != fileLength) {
        log.warn(
            "Expected to read {} bytes but only read {} bytes for snapshot id: {}",
            fileLength,
            readBytes,
            id);
      }

      tryUpdateExpirationDateAsync(id);

      Document metadata = gridFSFile.getMetadata();
      return Optional.of(
          new SnapshotData(
              bytesToWriteTo,
              SnapshotSerializerId.of(metadata.getString(SNAPSHOT_SERIALIZER_ID_FIELD)),
              UUID.fromString(metadata.getString(LAST_FACT_ID_FIELD))));
    } catch (MongoGridFSException e) {
      if (e.getMessage().contains("No file found")) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  @Override
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    String fileName = getFileName(id);

    Document metadata =
        new Document(PROJECTION_CLASS_FIELD, id.projectionClass().getName())
            .append(SNAPSHOT_SERIALIZER_ID_FIELD, snapshot.snapshotSerializerId().name())
            .append(LAST_FACT_ID_FIELD, snapshot.lastFactId().toString())
            .append(
                EXPIRE_AT_FIELD,
                Instant.now().plus(properties.getDeleteSnapshotStaleForDays(), ChronoUnit.DAYS));

    UUID aggregateId = id.aggregateId();
    if (aggregateId != null) {
      metadata.append(AGGREGATE_ID_FIELD, aggregateId.toString());
    }

    GridFSUploadOptions options = new GridFSUploadOptions().metadata(metadata);

    try (InputStream in = new ByteArrayInputStream(snapshot.serializedProjection())) {
      ObjectId objectId = gridFSBucket.uploadFromStream(fileName, in, options);
      tryDeleteOlderVersionsAsync(fileName, objectId);
    } catch (IOException e) {
      log.error("Error uploading snapshot to GridFS for id: {}", id, e);
    }
  }

  @Override
  public void remove(@NonNull SnapshotIdentifier id) {
    String fileName = getFileName(id);
    Bson query = Filters.eq(FILENAME_FIELD, fileName);
    gridFSBucket.find(query).forEach(gridFSFile -> {
      try {
        gridFSBucket.delete(gridFSFile.getId());
      } catch (Exception e) {
        log.debug("Failed to delete snapshot with filename: {}, possibly deleted by an async job before", fileName, e);
      }
    });
  }

  @VisibleForTesting
  void tryDeleteOlderVersionsAsync(String fileName, ObjectId keepId) {
    CompletableFuture.runAsync(
        () -> {
          try {
            deleteOlderVersions(fileName, keepId);
          } catch (Exception e) {
            log.warn("Failed to remove older versions for snapshot with filename: {}", fileName, e);
          }
        });
  }

  private void deleteOlderVersions(String fileName, ObjectId keepId) {
    Bson filter = Filters.eq(FILENAME_FIELD, fileName);
    gridFSBucket
        .find(filter)
        .forEach(
            gridFSFile -> {
              if (!gridFSFile.getId().asObjectId().getValue().equals(keepId)) {
                gridFSBucket.delete(gridFSFile.getId());
              }
            });
  }

  @VisibleForTesting
  void tryUpdateExpirationDateAsync(SnapshotIdentifier id) {
    CompletableFuture.runAsync(
        () -> {
          try {
            updateExpirationDate(id);
          } catch (Exception e) {
            log.warn("Failed to update expiration date for snapshot with id: {}", id, e);
          }
        });
  }

  private void updateExpirationDate(SnapshotIdentifier id) {
    // Updates many, because there may be multiple versions if store() failed to delete older ones
    // or if the executor
    // has not yet run
    filesCollection.updateMany(
        Filters.eq(FILENAME_FIELD, getFileName(id)),
        Updates.set(
            METADATA_EXPIRE_AT_FIELD,
            Instant.now().plus(properties.getDeleteSnapshotStaleForDays(), ChronoUnit.DAYS)));
  }

  private void scheduleCleanupTask() {
    // Grab the next midnight
    Instant nextMidnight =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    // Schedule task for each midnight starting from the next one
    CLEANUP_SCHEDULER.scheduleAtFixedRate(
        this::cleanupOldSnapshots,
        Duration.between(Instant.now(), nextMidnight).toMillis(),
        Duration.ofDays(1).toMillis(),
        java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  @VisibleForTesting
  public void cleanupOldSnapshots() {
    Instant now = Instant.now();
    // Find all documents with expireAt < now
    Bson filter = Filters.lt(METADATA_EXPIRE_AT_FIELD, now);
    try (MongoCursor<GridFSFile> filesToDelete = gridFSBucket.find(filter).iterator()) {
      while (filesToDelete.hasNext()) {
        GridFSFile file = filesToDelete.next();
        gridFSBucket.delete(file.getId());
      }
    }
  }

  @VisibleForTesting
  String getFileName(SnapshotIdentifier id) {
    return ScopedName.fromProjectionMetaData(id.projectionClass())
        .with(Optional.ofNullable(id.aggregateId()).map(UUID::toString).orElse("snapshot"))
        .asString();
  }
}
