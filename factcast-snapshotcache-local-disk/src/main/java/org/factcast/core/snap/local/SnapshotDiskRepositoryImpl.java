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

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.local.OldestModifiedFileHelper.PathWithLastModifiedDate;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.factus.snapshot.Snapshot;
import org.factcast.factus.snapshot.SnapshotId;

@Slf4j
public class SnapshotDiskRepositoryImpl implements SnapshotDiskRepository {
  private final File persistenceDirectory;
  private final long threshold;
  private final AtomicLong currentUsedSpace;
  private final LoadingCache<Path, ReadWriteLock> fileSystemLevelLocks;
  private final OldestModifiedFileHelper lastModifiedFileHelper;

  public SnapshotDiskRepositoryImpl(@NonNull InMemoryAndDiskSnapshotProperties properties) {
    File cacheRoot = new File(properties.getPathToSnapshots());
    Preconditions.checkState(
        cacheRoot.exists() && cacheRoot.isDirectory(),
        cacheRoot.getAbsolutePath() + " must exist and be a directory");
    this.persistenceDirectory = new File(cacheRoot, "/factcast/snapshots/");
    this.lastModifiedFileHelper = new OldestModifiedFileHelper(this.persistenceDirectory);
    this.threshold = (long) (properties.getMaxDiskSpace() * 0.9);
    this.fileSystemLevelLocks =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(1))
            .build(
                new CacheLoader<Path, ReadWriteLock>() {
                  @Override
                  public ReadWriteLock load(Path key) {
                    return new ReentrantReadWriteLock();
                  }
                });

    // Set the current used space at startup
    try {
      currentUsedSpace = new AtomicLong(getRepositoryTotalSize());
    } catch (IOException e) {
      log.error("Error getting the size of the snapshot directory", e);
      throw ExceptionHelper.toRuntime(e);
    }

    log.info(
        "SnapshotDiskRepositoryImpl initialized with path: {}, max available space: {}, requested max space: {}, currently used space {}",
        properties.getPathToSnapshots(),
        cacheRoot.getUsableSpace(),
        properties.getMaxDiskSpace(),
        currentUsedSpace.get());
  }

  @Override
  public void saveAsync(Snapshot value) {
    File target = SnapshotFileHelper.createFile(persistenceDirectory, value.id().key());
    target.getParentFile().mkdirs();
    Lock writeLock = fileSystemLevelLocks.getUnchecked(target.toPath()).writeLock();
    writeLock.lock();
    CompletableFuture.runAsync(
        () -> {
          try {
            long bytes =
                SnapshotSerializationHelper.serializeTo(
                    value, Files.newOutputStream(target.toPath()));
            this.currentUsedSpace.addAndGet(bytes);
            triggerCleanup();
          } catch (Exception e) {
            log.error("Error saving snapshot with id: {}", value.id(), e);
          } finally {
            writeLock.unlock();
          }
        });
  }

  @Override
  public void deleteAsync(SnapshotId id) {
    File target = SnapshotFileHelper.createFile(persistenceDirectory, id.key());
    Lock writeLock = fileSystemLevelLocks.getUnchecked(target.toPath()).writeLock();
    writeLock.lock();
    CompletableFuture.runAsync(
        () -> {
          try {
            if (target.exists()) {
              Path path = target.toPath();
              currentUsedSpace.addAndGet(-1 * Files.size(path));
              Files.delete(path);
            }
          } catch (IOException e) {
            log.error("Error deleting snapshot with id: {}", id, e);
          } finally {
            writeLock.unlock();
          }
        });
  }

  @Override
  public Optional<Snapshot> findById(SnapshotId id) {
    File persistenceFile = SnapshotFileHelper.createFile(persistenceDirectory, id.key());
    Lock readLock = fileSystemLevelLocks.getUnchecked(persistenceFile.toPath()).readLock();
    try {
      readLock.lock();
      if (!persistenceFile.exists()) {
        return Optional.empty();
      } else {
        SnapshotFileHelper.updateLastModified(persistenceFile);

        try (InputStream fis = Files.newInputStream(persistenceFile.toPath());
            InputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis)) {
          Snapshot snapshot = (Snapshot) ois.readObject();
          return Optional.of(snapshot);
        } catch (IOException e) {
          log.error(
              "Error reading snapshot with id: {} and path: {}", id, persistenceFile.getPath(), e);
          return Optional.empty();
        } catch (ClassNotFoundException e) {
          // Run async to avoid blocking the locks
          CompletableFuture.runAsync(() -> deleteAsync(id));
          log.error(
              "Error deserializing snapshot with id: {} and path: {}",
              id,
              persistenceFile.getPath(),
              e);
          return Optional.empty();
        }
      }
    } finally {
      readLock.unlock();
    }
  }

  /**
   * If the size of the new snapshot exceeds the 90% of configured max space, then we fetch the
   * oldest files and delete them until we are under the limit.
   */
  private void triggerCleanup() {
    if (threshold != 0 && currentUsedSpace.get() >= threshold)
      CompletableFuture.runAsync(this::cleanup);
  }

  @SneakyThrows
  private synchronized void cleanup() {
    while (currentUsedSpace.get() >= threshold) {
      Optional<PathWithLastModifiedDate> oldestFile =
          lastModifiedFileHelper.getOldestModifiedFile();

      while (oldestFile.isPresent()
          && Files.getLastModifiedTime(oldestFile.get().path())
              .equals(oldestFile.get().lastAccessTime())) {
        oldestFile = lastModifiedFileHelper.getOldestModifiedFile();
      }

      if (!oldestFile.isPresent()) {
        log.error("No more Snapshots to delete from Disk, but still over the limit");
        return;
      }

      Path path = oldestFile.get().path();
      try {
        if (Files.exists(path)) {
          long sizeOfFile = Files.size(path);
          Files.deleteIfExists(path);
          currentUsedSpace.addAndGet(-sizeOfFile);
        }
      } catch (IOException e) {
        log.error("Error deleting snapshot with path: {}", path, e);
      }
    }
  }

  private long getRepositoryTotalSize() throws IOException {
    try (Stream<Path> walk = Files.walk(persistenceDirectory.toPath())) {
      return walk.mapToLong(p -> p.toFile().length()).sum();
    }
  }
}
