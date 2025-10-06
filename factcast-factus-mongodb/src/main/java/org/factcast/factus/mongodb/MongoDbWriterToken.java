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
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.*;
import org.factcast.factus.projection.WriterToken;

// TODO consider making this a general ShedlockWriterToken implementation
@Slf4j
public class MongoDbWriterToken implements WriterToken {
  private @NonNull SimpleLock lock;
  private final LockConfiguration lockConfiguration;

  private final long keepaliveInterval;
  private final Timer scheduler;

  @Getter(AccessLevel.PROTECTED)
  @Setter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private @Nullable AtomicLong liveness;

  /**
   * Creates a WriterToken based on a SimpleLock acquired before, assuming it is a MongoDbLock. The
   * lock will be extended automatically in the background. The interval used to extend the lock
   * defaults to a third of the maximum lease from the LockConfiguration.
   *
   * @param lock the lock to be held during the lifetime of the token
   * @param lockConfiguration configuration used to extend the lock
   */
  protected MongoDbWriterToken(
      @NonNull SimpleLock lock, @NonNull LockConfiguration lockConfiguration) {
    this(lock, lockConfiguration, lockConfiguration.getLockAtMostFor().dividedBy(3));
  }

  @VisibleForTesting
  protected MongoDbWriterToken(
      @NonNull SimpleLock lock,
      @NonNull LockConfiguration lockConfiguration,
      @NonNull Duration keepaliveInterval) {
    this.lock = lock;
    this.lockConfiguration = lockConfiguration;
    this.liveness = new AtomicLong(System.currentTimeMillis());
    this.scheduler = new Timer(lockConfiguration.getName() + System.currentTimeMillis(), true);
    this.keepaliveInterval = keepaliveInterval.toMillis();
    startWriterTokenKeepalive();
  }

  /**
   * Attempts to extend the lock, which will only succeed of the lock is either free or held by the
   * instance.
   */
  @Override
  public boolean isValid() {
    if (alreadyClosed()) return false;
    // before extending check if the lock was extended in the last 20secs
    long lastCheck = liveness.get();
    if (System.currentTimeMillis() - lastCheck < keepaliveInterval) {
      return true;
    }
    Optional<SimpleLock> extendedLock =
        lock.extend(lockConfiguration.getLockAtMostFor(), lockConfiguration.getLockAtLeastFor());
    log.debug(
        "WriterToken {} validity check, when attempting to extend lock for: {}",
        extendedLock.isPresent() ? "passed" : "failed",
        lockConfiguration.getName());
    if (extendedLock.isPresent()) {
      this.lock = extendedLock.get();
      return true;
    }
    return false;
  }

  @Override
  public void close() {
    try {
      lock.unlock();
      // TODO: is a IllegalStateException possible here?
    } catch (IllegalStateException e) {
      log.warn("Failed to unlock, it is no longer valid: {}", e.getMessage());
    } finally {
      liveness = null;
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
            if (alreadyClosed()) {
              scheduler.cancel();
            } else {
              Optional<SimpleLock> extendedLock =
                  lock.extend(
                      lockConfiguration.getLockAtMostFor(), lockConfiguration.getLockAtLeastFor());
              if (extendedLock.isPresent()) {
                lock = extendedLock.get();
                liveness.set(System.currentTimeMillis());
              } else {
                // could not extend the lock.
                liveness = null;
                scheduler.cancel();
              }
              log.debug(
                  "{} to extend lock for projection: {}",
                  extendedLock.isPresent() ? "Succeeded" : "Failed",
                  lockConfiguration.getName());
            }
          }
        },
        keepaliveInterval,
        keepaliveInterval);
  }

  private boolean alreadyClosed() {
    return liveness == null;
  }
}
