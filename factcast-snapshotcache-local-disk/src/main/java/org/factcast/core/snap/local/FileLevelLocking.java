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

class FileLevelLocking {

  private final LoadingCache<Path, StampedLock> fileSystemLevelLocks;

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

  private enum Mode {
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

  private <T> CompletableFuture<T> withLockOnAsync(Mode mode, File toLockOn, Supplier<T> supplier) {
    Lock l = mode.acquire(fileSystemLevelLocks.getUnchecked(toLockOn.toPath()));
    l.lock();
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return supplier.get();
          } finally {
            l.unlock();
          }
        });
  }

  private CompletableFuture<Void> withLockOnAsync(Mode mode, File toLockOn, Runnable r) {
    Lock l = mode.acquire(fileSystemLevelLocks.getUnchecked(toLockOn.toPath()));
    l.lock();
    return CompletableFuture.runAsync(
        () -> {
          try {
            r.run();
          } finally {
            l.unlock();
          }
        });
  }

  private <T> T withLockOn(Mode mode, File toLockOn, Supplier<T> supplier) {
    Lock l = mode.acquire(fileSystemLevelLocks.getUnchecked(toLockOn.toPath()));
    l.lock();

    try {
      return supplier.get();
    } finally {
      l.unlock();
    }
  }

  private void withLockOn(Mode mode, File toLockOn, Runnable r) {
    Lock l = mode.acquire(fileSystemLevelLocks.getUnchecked(toLockOn.toPath()));
    l.lock();
    try {
      r.run();
    } finally {
      l.unlock();
    }
  }

  <T> T withReadLockOn(File toLockOn, java.util.function.Supplier<T> supplier) {
    return withLockOn(Mode.READ, toLockOn, supplier);
  }

  <T> CompletableFuture<T> withWriteLockOn(File toLockOn, java.util.function.Supplier<T> supplier) {
    return withLockOnAsync(Mode.WRITE, toLockOn, supplier);
  }

  void withReadLockOn(File toLockOn, Runnable r) {
    withLockOn(Mode.READ, toLockOn, r);
  }

  CompletableFuture<Void> withWriteLockOn(File toLockOn, Runnable r) {
    return withLockOnAsync(Mode.WRITE, toLockOn, r);
  }
}
