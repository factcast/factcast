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
package org.factcast.factus.mongodb;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.*;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import org.bson.Document;
import org.factcast.factus.projection.WriterToken;
import org.jspecify.annotations.Nullable;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @VisibleForTesting)
public class MongoDbWriterTokenManager {

  public static final String LOCK_COLLECTION_NAME = "factcast_locks";

  protected static final Duration MIN_LEASE_DURATION_SECONDS = Duration.ofSeconds(1);
  protected static final Duration MAX_LEASE_DURATION_SECONDS = Duration.ofSeconds(60);
  private static final long MAX_RETRY_INTERVAL_MILLISECONDS = 30_000;

  @NonNull private final LockProvider lockProvider;
  @NonNull private final String projectionKey;

  public static MongoDbWriterTokenManager create(
      @NonNull MongoDatabase database, @NonNull String projectionKey) {
    log.debug("Configuring lock provider: MongoDB.");
    MongoCollection<Document> lockTable = database.getCollection(LOCK_COLLECTION_NAME);
    return new MongoDbWriterTokenManager(new MongoLockProvider(lockTable), projectionKey);
  }

  @Nullable
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    final LockConfiguration lockConfiguration = getLockConfiguration(projectionKey + "_lock");
    Optional<SimpleLock> acquiredLock =
        tryToAcquireLock(lockConfiguration, ZonedDateTime.now().plus(maxWait));
    return acquiredLock.map(l -> new MongoDbWriterToken(l, lockConfiguration)).orElse(null);
  }

  private static LockConfiguration getLockConfiguration(String lockKey) {
    return new LockConfiguration(
        Instant.now(), lockKey, MAX_LEASE_DURATION_SECONDS, MIN_LEASE_DURATION_SECONDS);
  }

  @SuppressWarnings("java:S2142")
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
