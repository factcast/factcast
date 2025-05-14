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
package org.factcast.factus.redis;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import javax.annotation.Nullable;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.projection.WriterToken;
import org.redisson.api.*;

@Slf4j
public class RedisWriterToken implements WriterToken {
  private static final long CHECK_INTERVAL = Duration.ofSeconds(15).toMillis();
  private final @NonNull RFencedLock lock;

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private @Nullable AtomicLong liveness;

  @Getter(AccessLevel.PROTECTED)
  @VisibleForTesting
  private final Long token;

  @VisibleForTesting
  @Deprecated
  // keep signature for migration
  public RedisWriterToken(@NonNull RedissonClient redisson, @NonNull RLock lock) {
    this(replaceByFenced(redisson, lock));
  }

  // dirty hack to hopefully relock with fenced (unless someone else was faster)
  // sadly, this is not possible to wrap into a transaction or batch
  @SneakyThrows
  @VisibleForTesting
  protected static @NonNull RFencedLock replaceByFenced(
      @NonNull RedissonClient redisson, @NonNull RLock lock) {
    Preconditions.checkArgument(lock.isLocked());
    log.warn(
        "You are using deprecated code when creating a RedisWriterToken, by passing an RLock. Trying to upgrade your RLock to RFencedLock. Please consider updating your code asap.");

    RFencedLock fencedLock = redisson.getFencedLock(lock.getName());

    CompletableFuture<Void> cf = CompletableFuture.runAsync(fencedLock::lock);
    Thread.sleep(50); // i know... temporary code.
    lock.forceUnlock();
    cf.get(); // wait until locking of fenced on worked, or block forever.

    return fencedLock;
  }

  public RedisWriterToken(@NonNull RFencedLock lock) {
    Preconditions.checkArgument(lock.isLocked());
    this.lock = lock;
    this.token = lock.getToken();
    liveness = new AtomicLong(System.currentTimeMillis());
  }

  @Override
  public void close() {
    if (lockedAndOwned()) {
      lock.forceUnlock();
    }
    liveness = null;
  }

  private boolean lockedAndOwned() {
    return lock.isLocked() && lock.getToken().equals(token);
  }

  @Override
  public boolean isValid() {
    if (alreadyClosed()) return false; // it'll never come back

    long lastCheck = liveness.get();
    if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL) {
      return true;
    } else {
      // recheck
      if (lockedAndOwned()) {
        liveness.set(System.currentTimeMillis());
        return true;
      } else {
        close();
        return false;
      }
    }
  }

  private boolean alreadyClosed() {
    return liveness == null;
  }
}
