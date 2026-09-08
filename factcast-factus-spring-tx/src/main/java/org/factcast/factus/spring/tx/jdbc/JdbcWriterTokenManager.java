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
package org.factcast.factus.spring.tx.jdbc;

import jakarta.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.support.LockException;
import org.factcast.factus.projection.WriterToken;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionException;

/**
 * Hands out {@link WriterToken}s for a single projection, backed by a shedlock lease in a plain
 * JDBC table. Usable standalone, for projections that cannot extend {@link
 * AbstractSpringJdbcManagedProjection} or {@link AbstractSpringJdbcSubscribedProjection}.
 */
@Slf4j
public class JdbcWriterTokenManager {

  public static final String DEFAULT_LOCK_TABLE_NAME = "factcast_projection_locks";
  public static final Duration DEFAULT_LOCK_AT_MOST_FOR = Duration.ofSeconds(60);

  public static final Duration DEFAULT_LOCK_AT_LEAST_FOR = Duration.ZERO;

  /** Shortest lease that still leaves a sane renewal interval, see {@link JdbcWriterToken}. */
  public static final Duration MINIMUM_LOCK_AT_MOST_FOR = Duration.ofSeconds(1);

  /** Database products shedlock 7.10.0 has db-time lock statements for. */
  private static final String SUPPORTED_DATABASES =
      "PostgreSQL, CockroachDB, MySQL, MariaDB, Oracle, MS SQL Server, DB2, H2 and HSQLDB";

  private static final long INITIAL_RETRY_INTERVAL_MILLIS = 500;
  private static final long MAX_RETRY_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();
  private static final int KEEPALIVE_THREADS = 2;

  private static final ScheduledExecutorService KEEPALIVE_SCHEDULER =
      Executors.newScheduledThreadPool(KEEPALIVE_THREADS, keepaliveThreadFactory());

  private final LockProvider lockProvider;
  private final String projectionKey;
  private final String lockName;
  private final Duration lockAtMostFor;
  private final Duration lockAtLeastFor;
  private final ScheduledExecutorService scheduler;
  private final Timing timing;

  public JdbcWriterTokenManager(@NonNull LockProvider lockProvider, @NonNull String projectionKey) {
    this(lockProvider, projectionKey, DEFAULT_LOCK_AT_MOST_FOR, DEFAULT_LOCK_AT_LEAST_FOR);
  }

  public JdbcWriterTokenManager(
      @NonNull LockProvider lockProvider,
      @NonNull String projectionKey,
      @NonNull Duration lockAtMostFor,
      @NonNull Duration lockAtLeastFor) {
    this(
        lockProvider,
        projectionKey,
        lockAtMostFor,
        lockAtLeastFor,
        KEEPALIVE_SCHEDULER,
        Timing.SYSTEM);
  }

  JdbcWriterTokenManager(
      @NonNull LockProvider lockProvider,
      @NonNull String projectionKey,
      @NonNull Duration lockAtMostFor,
      @NonNull Duration lockAtLeastFor,
      @NonNull ScheduledExecutorService scheduler,
      @NonNull Timing timing) {
    if (lockAtMostFor.compareTo(MINIMUM_LOCK_AT_MOST_FOR) < 0) {
      throw new IllegalArgumentException(
          "lockAtMostFor must be at least %s but was %s: the lease is renewed every third of it, so anything shorter turns the keepalive into a hot loop against the database."
              .formatted(MINIMUM_LOCK_AT_MOST_FOR, lockAtMostFor));
    }
    this.lockProvider = lockProvider;
    this.projectionKey = projectionKey;
    this.lockName = ProjectionNames.lockName(projectionKey);
    this.lockAtMostFor = lockAtMostFor;
    this.lockAtLeastFor = lockAtLeastFor;
    this.scheduler = scheduler;
    this.timing = timing;
  }

  public static JdbcWriterTokenManager create(
      @NonNull JdbcTemplate jdbcTemplate, @NonNull String projectionKey) {
    return create(
        jdbcTemplate,
        projectionKey,
        DEFAULT_LOCK_TABLE_NAME,
        DEFAULT_LOCK_AT_MOST_FOR,
        DEFAULT_LOCK_AT_LEAST_FOR);
  }

  public static JdbcWriterTokenManager create(
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull String projectionKey,
      @NonNull String lockTableName,
      @NonNull Duration lockAtMostFor,
      @NonNull Duration lockAtLeastFor) {
    log.debug("Configuring lock provider: JDBC, table {}", lockTableName);
    return new JdbcWriterTokenManager(
        new JdbcTemplateLockProvider(lockProviderConfiguration(jdbcTemplate, lockTableName)),
        projectionKey,
        lockAtMostFor,
        lockAtLeastFor);
  }

  static JdbcTemplateLockProvider.Configuration lockProviderConfiguration(
      @NonNull JdbcTemplate jdbcTemplate, @NonNull String lockTableName) {
    return JdbcTemplateLockProvider.Configuration.builder()
        .withJdbcTemplate(jdbcTemplate)
        .withTableName(lockTableName)
        .withLockedByValue(lockedByValue())
        .usingDbTime()
        .build();
  }

  @Nullable
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    long deadline = timing.nanoTime() + maxWait.toNanos();
    long backoffMillis = INITIAL_RETRY_INTERVAL_MILLIS;
    try {
      while (true) {
        // the row's lock_until is CURRENT_TIMESTAMP + lease as of the statement, so the lease
        // starts before the round trip, not after it
        long leaseStartedAt = timing.nanoTime();
        Optional<SimpleLock> lock = tryLock();
        if (lock.isPresent()) {
          log.debug("Acquired lock {}", lockName);
          return new JdbcWriterToken(
              lock.get(),
              lockName,
              lockAtMostFor,
              lockAtLeastFor,
              leaseStartedAt,
              scheduler,
              timing::nanoTime);
        }
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - timing.nanoTime());
        if (remainingMillis <= 0) {
          log.trace("Giving up on lock {} after {}", lockName, maxWait);
          return null;
        }
        timing.sleep(Math.min(backoffMillis, remainingMillis));
        backoffMillis = Math.min(MAX_RETRY_INTERVAL_MILLIS, backoffMillis * 2);
      }
    } catch (InterruptedException e) {
      // deliberately not restoring the flag: callers retry without sleeping themselves, so a set
      // interrupt flag would turn their retry loop into a spin against the lock table
      log.info("Interrupted while trying to acquire lock {}", lockName);
      return null;
    }
  }

  private Optional<SimpleLock> tryLock() {
    LockConfiguration lockConfiguration =
        new LockConfiguration(Instant.now(), lockName, lockAtMostFor, lockAtLeastFor);
    try {
      return lockProvider.lock(lockConfiguration);
    } catch (DataAccessException | TransactionException e) {
      // shedlock lets a failure to even get a connection through unwrapped, and for us that is
      // indistinguishable from somebody else holding the lock
      log.warn("Attempt to acquire lock {} failed, will retry", lockName, e);
      return Optional.empty();
    } catch (LockException e) {
      throw new LockException(
          "Could not acquire the write lock for projection '%s' (lock name '%s', %d chars). Make sure the lock table exists and that its name column holds at least %d characters."
              .formatted(
                  projectionKey, lockName, lockName.length(), ProjectionNames.MAX_NAME_LENGTH),
          e);
    } catch (UnsupportedOperationException e) {
      // shedlock reports a database product it could not read at all as unsupported, so this is
      // not necessarily a permanent misconfiguration
      throw new LockException(
          "Could not acquire the write lock for projection '%s': its database does not support the server-side timestamps this lock is based on, or could not be asked which product it is. Supported are %s."
              .formatted(projectionKey, SUPPORTED_DATABASES),
          e);
    }
  }

  /**
   * shedlock matches extend and unlock on locked_by, so two instances sharing a hostname would
   * renew and release each other's lease.
   */
  private static String lockedByValue() {
    return hostname() + "/" + UUID.randomUUID();
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      log.debug("Cannot determine hostname", e);
      return "unknown-host";
    }
  }

  private static ThreadFactory keepaliveThreadFactory() {
    AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      Thread thread =
          new Thread(runnable, "factcast-jdbc-writertoken-keepalive-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }

  /** Seam that keeps the retry loop testable without waiting for wall-clock time. */
  interface Timing {
    Timing SYSTEM =
        new Timing() {
          @Override
          public long nanoTime() {
            return System.nanoTime();
          }

          @Override
          public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
          }
        };

    long nanoTime();

    void sleep(long millis) throws InterruptedException;
  }
}
