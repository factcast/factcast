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
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.*;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.model.*;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;

import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

@Slf4j
public class MongoDbSnapshotCache implements SnapshotCache {
  public static final String PROJECTION_CLASS_FIELD = "projectionClass";
  public static final String AGGREGATE_ID_FIELD = "aggregateId";
  public static final String SNAPSHOT_SERIALIZER_ID_FIELD = "snapshotSerializerId";
  public static final String LAST_FACT_ID_FIELD = "lastFactId";
  public static final String FILE_ID_FIELD = "fileId";
  public static final String EXPIRE_AT_FIELD = "expireAt";

  private static final ScheduledExecutorService CLEANUP_SCHEDULER = Executors.newScheduledThreadPool(1);

  // Recommended by the docs to use Majority for gridfs operations
  public static final TransactionOptions txnOptions =
      TransactionOptions.builder()
          .readPreference(ReadPreference.primary())
          .readConcern(ReadConcern.MAJORITY)
          .writeConcern(WriteConcern.MAJORITY)
          .build();

  private final MongoDbSnapshotProperties properties;
  private final MongoCollection<Document> collection;
  private final MongoClient mongoClient;

  @VisibleForTesting @Setter private GridFSBucket gridFSBucket;

  public MongoDbSnapshotCache(
      @NonNull MongoClient mongoClient,
      @NotNull String databaseName,
      @NonNull MongoDbSnapshotProperties properties) {
    this.mongoClient = mongoClient;
    this.properties = properties;
    MongoDatabase database = mongoClient.getDatabase(databaseName);
    this.collection = database.getCollection("factus_snapshot");

    // Grouped by projection, but only contains documents that have an aggregateId set
    String compoundIndexResult =
        collection.createIndex(
            Indexes.ascending(PROJECTION_CLASS_FIELD, AGGREGATE_ID_FIELD),
            new IndexOptions().partialFilterExpression(Filters.exists(AGGREGATE_ID_FIELD)));
    log.debug(
        "Create compound index on factus_snapshot collection returned: {}", compoundIndexResult);

    // Second index is for the projection class only, which is used for snapshots that are not
    // related to an aggregate
    String aggregateIdIndexResult =
        collection.createIndex(Indexes.ascending(PROJECTION_CLASS_FIELD));
    log.debug(
        "Create aggregate ID index on factus_snapshot collection returned: {}",
        aggregateIdIndexResult);

    // Third index for TTL management of documents, will be used for quick lookup of expired docs to be deleted
    collection.createIndex(Indexes.ascending(EXPIRE_AT_FIELD));

    // Create the GridFS bucket with helper class to store binaries
    gridFSBucket = GridFSBuckets.create(database);
    // Warmup the gridFS bucket by doing a dummy upload and delete (Its forbidden later in txns)
    byte[] tiny = new byte[] {0};
    ObjectId tmp = gridFSBucket.uploadFromStream("_warmup_", new ByteArrayInputStream(tiny));
    gridFSBucket.delete(tmp); // cleanup

    // Schedule cleanup task
    scheduleCleanupTask();
  }

  @Override
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    Document query = createQueryById(id);
    Document result = collection.find(query).first();

    if (result == null) {
      return Optional.empty();
    }

    // Field ID of the binary in GridFS
    ObjectId fileId = result.getObjectId(FILE_ID_FIELD);

    byte[] bytes;
    // Stream the GridFS file to a destination (here, a file on disk)
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      gridFSBucket.downloadToStream(fileId, out);
      bytes = out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    String serializerId = result.getString(SNAPSHOT_SERIALIZER_ID_FIELD);
    UUID lastFactId = UUID.fromString(result.getString(LAST_FACT_ID_FIELD));

    tryUpdateExpirationDateAsync(id);

    return Optional.of(new SnapshotData(bytes, SnapshotSerializerId.of(serializerId), lastFactId));
  }

  @Override
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    Document doc =
        new Document(PROJECTION_CLASS_FIELD, id.projectionClass().getName())
            .append(SNAPSHOT_SERIALIZER_ID_FIELD, snapshot.snapshotSerializerId().name())
            .append(LAST_FACT_ID_FIELD, snapshot.lastFactId().toString())
            .append(
                EXPIRE_AT_FIELD,
                Instant.now().plus(properties.getDeleteSnapshotStaleForDays(), ChronoUnit.DAYS));

    UUID aggregateId = id.aggregateId();
    if (aggregateId != null) {
      doc.append(AGGREGATE_ID_FIELD, aggregateId.toString());
    }

    Document query = createQueryById(id);
    String binaryTittle = getBinaryTittle(id);

    try (ClientSession session = mongoClient.startSession()) {
      session.withTransaction(
          () -> {
            ObjectId fileId;
            try (InputStream in = new ByteArrayInputStream(snapshot.serializedProjection())) {
              fileId = gridFSBucket.uploadFromStream(session, binaryTittle, in);
            } catch (IOException e) {
              log.error("Error uploading snapshot to GridFS for id: {}", id, e);
              throw new RuntimeException(e);
            }

            doc.append(FILE_ID_FIELD, fileId);

            collection.replaceOne(session, query, doc, new ReplaceOptions().upsert(true));
            return true;
          },
          txnOptions);
    }
  }

  @Override
  public void remove(@NonNull SnapshotIdentifier id) {
    Document query = createQueryById(id);
    Document result = collection.find(query).first();

    if (result != null) {
      ObjectId fileId = result.getObjectId(FILE_ID_FIELD);

      try (ClientSession session = mongoClient.startSession()) {
        session.withTransaction(
            () -> {
              gridFSBucket.delete(session, fileId);

              collection.deleteOne(session, query);
              return true;
            },
            txnOptions);
      }
    }
  }

  private void tryUpdateExpirationDateAsync(SnapshotIdentifier id) {
    CompletableFuture.runAsync(() -> {
      try {
        collection.updateOne(createQueryById(id), Updates.set(EXPIRE_AT_FIELD, Instant.now().plus(properties.getDeleteSnapshotStaleForDays(), ChronoUnit.DAYS)));
      } catch (Exception e) {
        log.warn("Failed to update expiration date for snapshot with id: {}", id, e);
      }
    });
  }

  private void scheduleCleanupTask() {
    // Grab the next midnight
    Instant nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    // Schedule task for each midnight starting from the next one
    CLEANUP_SCHEDULER.scheduleAtFixedRate(
            this::cleanupOldSnapshots,
            Duration.between(Instant.now(), nextMidnight).toMillis(),
            Duration.ofDays(1).toMillis(),
            java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  @VisibleForTesting
   public void cleanupOldSnapshots() {
    try (ClientSession session = mongoClient.startSession()) {
      session.withTransaction(
              () -> {
                Instant now = Instant.now();
                // Find all documents with expireAt < now
                Bson filter = Filters.lt(EXPIRE_AT_FIELD, now);
                try (MongoCursor<Document> docsToDelete = collection.find(session, filter).iterator()) {
                  while (docsToDelete.hasNext()) {
                    Document doc = docsToDelete.next();
                    ObjectId fileId = doc.getObjectId(FILE_ID_FIELD);
                    gridFSBucket.delete(session, fileId);
                    collection.deleteOne(session, Filters.eq("_id", doc.getObjectId("_id")));
                  }
                }
                return true;
              },
              txnOptions);
    } catch (Exception e) {
      log.error("Error during cleanup of old snapshots", e);
    }
  }

  private String getBinaryTittle(SnapshotIdentifier id) {
    return id.projectionClass().getName()
        + Optional.ofNullable(id.aggregateId()).map(UUID::toString).orElse("");
  }

  private Document createQueryById(SnapshotIdentifier id) {
    Document query = new Document(PROJECTION_CLASS_FIELD, id.projectionClass().getName());

    if (id.aggregateId() != null) {
      query.append(AGGREGATE_ID_FIELD, id.aggregateId().toString());
    }
    return query;
  }
}
