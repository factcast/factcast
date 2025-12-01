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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.*;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import org.bson.Document;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.projection.WriterToken;

@Slf4j
@SuppressWarnings({"java:S1133", "java:S2142"})
abstract class AbstractMongoDbProjection implements MongoDbProjection {
  public static final String STATE_COLLECTION = "states";
  public static final String LOCK_COLLECTION = "locks";
  public static final String PROJECTION_CLASS_FIELD = "projectionKey";
  public static final String LAST_FACT_ID_FIELD = "lastFactId";
  public static final String LAST_FACT_SERIAL_FIELD = "lastFactSerial";

  // Time after which the lock is released if unlock() is called
  protected static final Duration MIN_LEASE_DURATION_SECONDS = Duration.ofSeconds(1);
  // Time after which the lock is automatically released
  protected static final Duration MAX_LEASE_DURATION_SECONDS = Duration.ofSeconds(60);
  private static final long MAX_RETRY_INTERVAL_MILLISECONDS = 30_000;

  @Getter @NonNull private final MongoDatabase mongoDb;
  @Getter @NonNull protected final String projectionKey;
  @NonNull private final MongoCollection<Document> stateCollection;
  @NonNull private final LockProvider lockProvider;
  @Getter @Setter private SimpleLock lock;

  protected AbstractMongoDbProjection(
      @NonNull MongoClient mongoClient, @NonNull String databaseName) {
    this(mongoClient.getDatabase(databaseName));
  }

  protected AbstractMongoDbProjection(@NonNull MongoDatabase mongoDb) {
    this(
        mongoDb,
        // Collections are created automatically when non-existent
        mongoDb.getCollection(STATE_COLLECTION),
        createLockClient(mongoDb));
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
    this.lockProvider = lockProvider;
  }

  private static LockProvider createLockClient(MongoDatabase database) {
    log.debug("Configuring lock provider: MongoDB.");
    MongoCollection<Document> lockTable = database.getCollection(LOCK_COLLECTION);
    return new MongoLockProvider(lockTable);
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
    final LockConfiguration lockConfiguration = getLockConfiguration(getLockKey());
    Optional<SimpleLock> acquiredLock =
        tryToAcquireLock(lockConfiguration, ZonedDateTime.now().plus(maxWait));
    this.lock = acquiredLock.orElse(null);
    return acquiredLock.map(l -> new MongoDbWriterToken(l, lockConfiguration)).orElse(null);
  }

  private String getLockKey() {
    return projectionKey + "_lock";
  }

  private static LockConfiguration getLockConfiguration(String lockKey) {
    return new LockConfiguration(
        Instant.now(), lockKey, MAX_LEASE_DURATION_SECONDS, MIN_LEASE_DURATION_SECONDS);
  }

  /**
   * Attempts to acquire the lock until retryUntil has passed. Retry interval is increasing until
   * MAX_RETRY_INTERVAL_MILLISECONDS is reached, starting with 0,5 seconds.
   */
  private Optional<SimpleLock> tryToAcquireLock(
      @NonNull LockConfiguration lockConfig, @NonNull ZonedDateTime retryUntil) {
    try {
      long retryBackoffDuration = 500;
      do {
        log.debug("Trying to acquire lock for projection: {}", projectionKey);
        Optional<SimpleLock> acquiredLock = lockProvider.lock(lockConfig);
        if (acquiredLock.isPresent()) {
          log.debug("Acquired lock for projection: {}", projectionKey);
          return acquiredLock;
        }
        Thread.sleep(retryBackoffDuration);
        retryBackoffDuration =
            Math.min(MAX_RETRY_INTERVAL_MILLISECONDS * 1000, retryBackoffDuration * 2);
      } while (ZonedDateTime.now().isBefore(retryUntil));
    } catch (InterruptedException e) {
      log.info("Interrupted while trying to acquire lock: {}", e.getMessage());
      Thread.currentThread().interrupt();
    }
    return Optional.empty();
  }
}
