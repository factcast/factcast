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
package org.factcast.core.snap.local;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;
import lombok.Getter;

@SuppressWarnings("LombokGetterMayBeUsed")
class FileLevelLocking {

  @VisibleForTesting @Getter final LoadingCache<Path, StampedLock> fileSystemLevelLocks;

  FileLevelLocking() {
    this.fileSystemLevelLocks =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(1))
            .build(
                new CacheLoader<Path, StampedLock>() {
                  @Override
                  public StampedLock load(Path key) {
                    return new StampedLock();
                  }
                });
  }

  // convenience methods
  // READ

  <T> T withReadLockOn(File toLockOn, java.util.function.Supplier<T> supplier) {
    return withLockOn(Mode.READ, toLockOn, supplier);
  }

  void withReadLockOn(File toLockOn, Runnable r) {
    withLockOn(Mode.READ, toLockOn, r);
  }

  CompletableFuture<Void> withReadLockOnAsync(File toLockOn, Runnable r) {
    return withLockOnAsync(Mode.READ, toLockOn, r);
  }

  // WRITE

  CompletableFuture<Void> withWriteLockOnAsync(File toLockOn, Runnable r) {
    return withLockOnAsync(Mode.WRITE, toLockOn, r);
  }

  // helpers
  private void withLockOn(Mode mode, File toLockOn, Runnable r) {
    runAndUnlock(r, lockOn(mode, toLockOn));
  }

  @VisibleForTesting
  Lock lockOn(Mode mode, File toLockOn) {
    Lock l = mode.acquire(fileSystemLevelLocks.getUnchecked(toLockOn.toPath()));
    l.lock();
    return l;
  }

  private CompletableFuture<Void> withLockOnAsync(Mode mode, File toLockOn, Runnable r) {
    // needs to be evaulated right now
    Lock l = lockOn(mode, toLockOn);
    return CompletableFuture.runAsync(() -> runAndUnlock(r, l));
  }

  private <T> T withLockOn(Mode mode, File toLockOn, Supplier<T> supplier) {
    return supplyAndUnlock(supplier, lockOn(mode, toLockOn));
  }

  @VisibleForTesting
  static <T> T supplyAndUnlock(Supplier<T> supplier, Lock l) {
    try {
      return supplier.get();
    } finally {
      l.unlock();
    }
  }

  @VisibleForTesting
  static void runAndUnlock(Runnable r, Lock l) {
    try {
      r.run();
    } finally {
      l.unlock();
    }
  }

  enum Mode {
    READ {
      @Override
      Lock acquire(StampedLock lock) {
        return lock.asReadWriteLock().readLock();
      }
    },
    WRITE {
      @Override
      Lock acquire(StampedLock lock) {
        return lock.asReadWriteLock().writeLock();
      }
    };

    abstract Lock acquire(StampedLock lock);
  }
}
