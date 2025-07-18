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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonBinarySubType;
import org.bson.Document;
import org.bson.types.Binary;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;

@SuppressWarnings("deprecation")
@Slf4j
public class MongoDbSnapshotCache implements SnapshotCache {
  public static final String PROJECTION_CLASS_FIELD = "projectionClass";
  public static final String AGGREGATE_ID_FIELD = "aggregateId";
  public static final String SNAPSHOT_SERIALIZER_ID_FIELD = "snapshotSerializerId";
  public static final String LAST_FACT_ID_FIELD = "lastFactId";
  public static final String SERIALIZED_PROJECTION_FIELD = "serializedProjection";
  public static final String EXPIRE_AT_FIELD = "expireAt";

  private final MongoDbSnapshotProperties properties;
  private final MongoCollection<Document> collection;

  public MongoDbSnapshotCache(
      @NonNull MongoClient mongoClient,
      @NotNull String databaseName,
      @NonNull MongoDbSnapshotProperties properties) {
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

    // Third index for TTL management of documents, expires the document after 0 seconds of the
    // `expireAt` field. So, immediately.
    collection.createIndex(
        Indexes.ascending(EXPIRE_AT_FIELD), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
  }

  @Override
  public @NonNull Optional<SnapshotData> find(@NonNull SnapshotIdentifier id) {
    Document query = getDocumentById(id);

    Document result = collection.find(query).first();

    if (result != null) {
      Binary binary = result.get(SERIALIZED_PROJECTION_FIELD, Binary.class);
      byte[] bytes = binary.getData();
      String serializerId = result.getString(SNAPSHOT_SERIALIZER_ID_FIELD);
      UUID lastFactId = UUID.fromString(result.getString(LAST_FACT_ID_FIELD));

      tryUpdateExpirationDate(id);

      return Optional.of(
          new SnapshotData(bytes, SnapshotSerializerId.of(serializerId), lastFactId));
    }

    return Optional.empty();
  }

  private void tryUpdateExpirationDate(SnapshotIdentifier id) {
    try {
      collection.updateOne(
          getDocumentById(id),
          Updates.set(
              EXPIRE_AT_FIELD,
              Instant.now().plus(properties.getDeleteSnapshotStaleForDays(), ChronoUnit.DAYS)));
    } catch (Exception e) {
      log.warn("Failed to update expiration date for snapshot with id: {}", id, e);
    }
  }

  @Override
  public void store(@NonNull SnapshotIdentifier id, @NonNull SnapshotData snapshot) {
    Binary bytes = new Binary(BsonBinarySubType.BINARY, snapshot.serializedProjection());

    Document doc =
        new Document(PROJECTION_CLASS_FIELD, id.projectionClass().getName())
            .append(SNAPSHOT_SERIALIZER_ID_FIELD, snapshot.snapshotSerializerId().name())
            .append(LAST_FACT_ID_FIELD, snapshot.lastFactId().toString())
            // Document limit for binary data(16MB)
            .append(SERIALIZED_PROJECTION_FIELD, bytes);

    if (id.aggregateId() != null) {
      doc.append(AGGREGATE_ID_FIELD, id.aggregateId().toString());
    }

    // Add expiration time to the document
    doc.append(
        EXPIRE_AT_FIELD,
        Instant.now().plus(properties.getDeleteSnapshotStaleForDays(), ChronoUnit.DAYS));

    Document query = getDocumentById(id);
    collection.replaceOne(query, doc, new ReplaceOptions().upsert(true));
  }

  @Override
  public void remove(@NonNull SnapshotIdentifier id) {
    collection.deleteOne(getDocumentById(id));
  }

  private Document getDocumentById(SnapshotIdentifier id) {
    Document query = new Document(PROJECTION_CLASS_FIELD, id.projectionClass().getName());

    if (id.aggregateId() != null) {
      query.append(AGGREGATE_ID_FIELD, id.aggregateId().toString());
    }
    return query;
  }
}
