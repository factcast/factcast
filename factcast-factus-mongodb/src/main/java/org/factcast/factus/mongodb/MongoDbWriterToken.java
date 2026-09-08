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
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.*;
import org.factcast.factus.projection.WriterToken;

@Slf4j
public class MongoDbWriterToken implements WriterToken {
  private final AtomicReference<SimpleLock> lock;
  private final LockConfiguration lockConfiguration;

  private final long keepaliveInterval;
  private final Timer scheduler;

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean lockLost = new AtomicBoolean(false);

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private final AtomicLong liveness = new AtomicLong(System.nanoTime());

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
    this.lock = new AtomicReference<>(lock);
    this.lockConfiguration = lockConfiguration;
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
    if (closed.get() || lockLost.get()) {
      return false;
    }
    return System.nanoTime() - liveness.get() < lockConfiguration.getLockAtMostFor().toNanos();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    scheduler.cancel();
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

  private void extendLock() {
    if (closed.get()) {
      return;
    }
    try {
      Optional<SimpleLock> extendedLock =
          lock.get()
              .extend(lockConfiguration.getLockAtMostFor(), lockConfiguration.getLockAtLeastFor());
      if (extendedLock.isPresent()) {
        lock.set(extendedLock.get());
        liveness.set(System.nanoTime());
        log.debug("Extended lock for projection: {}", lockConfiguration.getName());
        return;
      }
      log.warn("Failed to extend lock for projection: {}", lockConfiguration.getName());
    } catch (IllegalStateException e) {
      log.warn(
          "Failed to extend lock for projection: {}, {}",
          lockConfiguration.getName(),
          e.getMessage());
    }
    invalidateLock();
  }

  private void invalidateLock() {
    lockLost.set(true);
    scheduler.cancel();
  }
}
