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
package org.factcast.factus.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import java.time.Duration;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import org.bson.Document;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.WriterToken;

@Slf4j
@SuppressWarnings("java:S1133")
public abstract class AbstractMongoDbProjection implements MongoDbProjection {
  @Getter @NonNull private final MongoDatabase mongoDb;
  @Getter @NonNull protected final String projectionKey;
  @NonNull private final MongoCollection<Document> stateCollection;
  @NonNull private final MongoDbWriterTokenManager writerTokenAcquirer;

  protected AbstractMongoDbProjection(
      @NonNull MongoClient mongoClient, @NonNull String databaseName) {
    this(mongoClient.getDatabase(databaseName));
  }

  protected AbstractMongoDbProjection(@NonNull MongoDatabase mongoDb) {
    this.mongoDb = mongoDb;
    this.projectionKey = this.getScopedName().asString();
    // Collections are created automatically when non-existent
    this.stateCollection = mongoDb.getCollection(STATE_COLLECTION_NAME);
    this.writerTokenAcquirer = MongoDbWriterTokenManager.create(mongoDb, projectionKey);
  }

  // Only for testing purposes.
  protected AbstractMongoDbProjection(
      @NonNull MongoDatabase mongoDb,
      @NonNull MongoCollection<Document> stateCollection,
      @NonNull LockProvider lockProvider) {
    this.mongoDb = mongoDb;
    this.projectionKey = this.getScopedName().asString();
    // Collections are created automatically when non-existent
    this.stateCollection = stateCollection;
    this.writerTokenAcquirer = new MongoDbWriterTokenManager(lockProvider, projectionKey);
  }

  @Override
  public FactStreamPosition factStreamPosition() {
    final Document state = stateCollection.find(getProjectionStateByKey(projectionKey)).first();

    return state != null
        ? FactStreamPosition.of(
            state.get(LAST_FACT_ID_FIELD, UUID.class), state.getLong(LAST_FACT_SERIAL_FIELD))
        : null;
  }

  private Document getProjectionStateByKey(String projectionKey) {
    return new Document(PROJECTION_CLASS_FIELD, projectionKey);
  }

  @Override
  public void factStreamPosition(@NonNull FactStreamPosition position) {
    UUID factStreamPosition = position.factId();
    if (factStreamPosition != null) {
      Document positionDocument =
          new Document(PROJECTION_CLASS_FIELD, projectionKey)
              .append(LAST_FACT_ID_FIELD, factStreamPosition)
              .append(LAST_FACT_SERIAL_FIELD, position.serial());
      stateCollection.replaceOne(
          getProjectionStateByKey(projectionKey),
          positionDocument,
          new ReplaceOptions().upsert(true));
    }
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return writerTokenAcquirer.acquireWriteToken(maxWait);
  }
}
