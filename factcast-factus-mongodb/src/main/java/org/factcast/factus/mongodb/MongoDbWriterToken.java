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
package org.factcast.factus.mongodb;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.*;
import org.factcast.factus.projection.WriterToken;

@Slf4j
public class MongoDbWriterToken implements WriterToken {
  private final AtomicReference<SimpleLock> lock;
  private final LockConfiguration lockConfiguration;
  private final long leaseNanos;
  private final LongSupplier nanoTime;

  private final long keepaliveInterval;
  private final Timer scheduler;

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean lockLost = new AtomicBoolean(false);

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private final AtomicLong liveness;

  /**
   * Creates a WriterToken based on a SimpleLock acquired before, assuming it is a MongoDbLock. The
   * lock will be extended automatically in the background. The interval used to extend the lock
   * defaults to a third of the maximum lease from the LockConfiguration.
   *
   * @param lock the lock to be held during the lifetime of the token
   * @param lockConfiguration configuration used to extend the lock
   */
  public MongoDbWriterToken(
      @NonNull SimpleLock lock, @NonNull LockConfiguration lockConfiguration) {
    this(lock, lockConfiguration, lockConfiguration.getLockAtMostFor().dividedBy(3));
  }

  @VisibleForTesting
  protected MongoDbWriterToken(
      @NonNull SimpleLock lock,
      @NonNull LockConfiguration lockConfiguration,
      @NonNull Duration keepaliveInterval) {
    this(lock, lockConfiguration, keepaliveInterval, System::nanoTime);
  }

  @VisibleForTesting
  MongoDbWriterToken(
      @NonNull SimpleLock lock,
      @NonNull LockConfiguration lockConfiguration,
      @NonNull Duration keepaliveInterval,
      @NonNull LongSupplier nanoTime) {
    this.lock = new AtomicReference<>(lock);
    this.lockConfiguration = lockConfiguration;
    this.leaseNanos = lockConfiguration.getLockAtMostFor().toNanos();
    this.nanoTime = nanoTime;
    this.liveness = new AtomicLong(nanoTime.getAsLong());
    this.scheduler = new Timer(lockConfiguration.getName() + System.currentTimeMillis(), true);
    this.keepaliveInterval = keepaliveInterval.toMillis();
    startWriterTokenKeepalive();
  }

  /**
   * Reports whether the lease acquired by the last successful extension still covers now. A
   * shedlock SimpleLock can only be extended once, so extending is left to the keepalive.
   */
  @Override
  public boolean isValid() {
    return !closed.get() && !lockLost.get() && leaseIsLive();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    scheduler.cancel();
    if (lockLost.get() || !leaseIsLive()) {
      // MongoLockProvider's unlock matches on the lock name alone, so once our lease is gone that
      // document may already describe somebody else's lock
      log.debug("Not releasing lock {}, its lease is gone", lockConfiguration.getName());
      return;
    }
    try {
      lock.get().unlock();
    } catch (IllegalStateException e) {
      log.warn("Failed to unlock, it is no longer valid: {}", e.getMessage());
    }
  }

  /**
   * Adding a keep-alive mechanism here, because the shedlock KeepAliveLock does not support
   * extending the lock manually, which we need for the isValid implementation in the WriterToken.
   */
  private void startWriterTokenKeepalive() {
    scheduler.schedule(
        new TimerTask() {
          @Override
          public void run() {
            extendLock();
          }
        },
        keepaliveInterval,
        keepaliveInterval);
  }

  private boolean leaseIsLive() {
    return nanoTime.getAsLong() - liveness.get() < leaseNanos;
  }

  /**
   * An escaping exception would kill the Timer thread and with it the keepalive, so this must never
   * throw.
   */
  @VisibleForTesting
  void extendLock() {
    if (closed.get() || lockLost.get()) {
      return;
    }
    if (!leaseIsLive()) {
      // MongoLockProvider only extends a document whose lockUntil is still in the future, so past
      // our own lease there is nothing left to retry
      log.warn("Lease of lock {} ran out, invalidating writer token", lockConfiguration.getName());
      invalidateLock();
      return;
    }
    long extendedFrom = nanoTime.getAsLong();
    try {
      Optional<SimpleLock> extendedLock =
          lock.get()
              .extend(lockConfiguration.getLockAtMostFor(), lockConfiguration.getLockAtLeastFor());
      if (extendedLock.isPresent()) {
        lock.set(extendedLock.get());
        liveness.set(extendedFrom);
        log.debug("Extended lock for projection: {}", lockConfiguration.getName());
      } else {
        log.warn("Lost lock for projection: {}", lockConfiguration.getName());
        invalidateLock();
      }
    } catch (RuntimeException e) {
      // a failing round trip does not tell us the lease is gone, so let the next tick retry
      log.warn(
          "Failed to extend lock for projection: {}, will retry", lockConfiguration.getName(), e);
    }
  }

  private void invalidateLock() {
    lockLost.set(true);
    scheduler.cancel();
  }
}
