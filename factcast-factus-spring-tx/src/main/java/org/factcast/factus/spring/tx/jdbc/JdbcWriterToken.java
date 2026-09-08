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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.factus.projection.WriterToken;

/**
 * WriterToken backed by a shedlock lease that is extended by exactly one scheduled task, running
 * every third of the lease duration.
 *
 * <p>{@link #isValid()} is a read-only check against the age of the last successful extension, so
 * it neither performs I/O nor races with the extending task.
 */
@Slf4j
public class JdbcWriterToken implements WriterToken {

  private final AtomicReference<SimpleLock> lock;
  private final String lockName;
  private final Duration lockAtMostFor;
  private final Duration lockAtLeastFor;
  private final long leaseNanos;
  private final LongSupplier nanoTime;

  private final AtomicLong lastSuccessfulExtension;
  private final AtomicBoolean invalidated = new AtomicBoolean(false);
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicReference<ScheduledFuture<?>> keepalive = new AtomicReference<>();

  JdbcWriterToken(
      @NonNull SimpleLock lock,
      @NonNull String lockName,
      @NonNull Duration lockAtMostFor,
      @NonNull Duration lockAtLeastFor,
      @NonNull ScheduledExecutorService scheduler,
      @NonNull LongSupplier nanoTime) {
    this.lock = new AtomicReference<>(lock);
    this.lockName = lockName;
    this.lockAtMostFor = lockAtMostFor;
    this.lockAtLeastFor = lockAtLeastFor;
    this.leaseNanos = lockAtMostFor.toNanos();
    this.nanoTime = nanoTime;
    this.lastSuccessfulExtension = new AtomicLong(nanoTime.getAsLong());

    long intervalMillis = Math.max(1, lockAtMostFor.dividedBy(3).toMillis());
    keepalive.set(
        scheduler.scheduleAtFixedRate(
            this::extendLease, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS));
  }

  @Override
  public boolean isValid() {
    if (closed.get() || invalidated.get()) {
      return false;
    }
    return nanoTime.getAsLong() - lastSuccessfulExtension.get() < leaseNanos;
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    cancelKeepalive();
    try {
      lock.get().unlock();
    } catch (IllegalStateException e) {
      log.debug("Lock {} was already released: {}", lockName, e.getMessage());
    }
  }

  private void extendLease() {
    if (closed.get() || invalidated.get()) {
      cancelKeepalive();
      return;
    }
    try {
      Optional<SimpleLock> extended = lock.get().extend(lockAtMostFor, lockAtLeastFor);
      if (extended.isPresent()) {
        lock.set(extended.get());
        lastSuccessfulExtension.set(nanoTime.getAsLong());
        log.trace("Extended lock {}", lockName);
      } else {
        log.warn("Lost lock {}, invalidating writer token", lockName);
        invalidate();
      }
    } catch (RuntimeException e) {
      log.warn("Failed to extend lock {}, invalidating writer token", lockName, e);
      invalidate();
    }
  }

  private void invalidate() {
    invalidated.set(true);
    cancelKeepalive();
  }

  private void cancelKeepalive() {
    ScheduledFuture<?> scheduled = keepalive.get();
    if (scheduled != null) {
      scheduled.cancel(false);
    }
  }
}
