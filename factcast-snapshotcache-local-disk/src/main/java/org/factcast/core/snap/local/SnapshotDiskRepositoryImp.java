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
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.snapshot.Snapshot;
import org.factcast.factus.snapshot.SnapshotId;

@Slf4j
public class SnapshotDiskRepositoryImp implements SnapshotDiskRepository {
  private final File persistenceDirectory;
  private final long reservedSpace;
  private final AtomicLong currentUsedSpace = new AtomicLong(0);
  private final LoadingCache<SnapshotId, ReadWriteLock> fileSystemLevelLocks;
  private final Deque<Path> lastModifiedPaths = new LinkedList<>();

  public SnapshotDiskRepositoryImp(InMemoryAndDiskSnapshotProperties properties) {
    this.persistenceDirectory = new File(properties.getPathToSnapshots());
    this.reservedSpace = properties.getMaxDiskSpace();
    this.fileSystemLevelLocks =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(1))
            .build(
                new CacheLoader<SnapshotId, ReadWriteLock>() {
                  @Override
                  public ReadWriteLock load(SnapshotId snapshotId) {
                    return new ReentrantReadWriteLock();
                  }
                });

    // Set the current used space at startup
    try {
      currentUsedSpace.set(getFolderSize(persistenceDirectory));
    } catch (IOException e) {
      log.error("Error getting the size of the snapshot directory", e);
      throw new RuntimeException(e);
    }
    log.info(
        "SnapshotDiskRepositoryImp initialized with path: {}, max available space: {}, requested max space: {}, currently used space {}",
        properties.getPathToSnapshots(),
        persistenceDirectory.getUsableSpace(),
        properties.getMaxDiskSpace(),
        currentUsedSpace.get());
  }

  @Override
  public Optional<Snapshot> findById(SnapshotId key) {
    Lock readLock = fileSystemLevelLocks.getUnchecked(key).readLock();
    try {
      readLock.lock();

      File persistenceFile = new File(persistenceDirectory, getPathFromSnapshotId(key).toString());
      if (!persistenceFile.exists()) {
        return Optional.empty();
      }

      try (FileInputStream fileInputStream = new FileInputStream(persistenceFile);
          ObjectInputStream ois = new ObjectInputStream(fileInputStream)) {
        Snapshot snapshot = (Snapshot) ois.readObject();

        // TODO update the file access time or modified, or whatever :D
        return Optional.ofNullable(snapshot);
      }
    } catch (IOException | ClassNotFoundException e) {
      // TODO, cleanup if not readable?
      log.error("Error reading snapshot with id: {}", key, e);
      return Optional.empty();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void saveAsync(Snapshot value) {
    Lock writeLock = fileSystemLevelLocks.getUnchecked(value.id()).writeLock();
    writeLock.lock();
    CompletableFuture.runAsync(
        () -> {
          try {
            File persistenceFile = new File(persistenceDirectory, value.id().key());

            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(byteOut)) {
              oos.writeObject(value);
              oos.flush();
            }

            // Know the size of the new object
            // Know the size of the folder
            // If exceeds then ---- delete? not save?
            // saving this file - starting async cleanup of old files
            // if not save

            checkSizeAndCleanupOldSnapshotsIfNeeded(byteOut.size());

            try (FileOutputStream fileOutputStream = new FileOutputStream(persistenceFile);
                ObjectOutputStream oos = new ObjectOutputStream(fileOutputStream)) {

              byteOut.writeTo(fileOutputStream);
              byteOut.flush();
            }
          } catch (IOException e) {
            log.error("Error saving snapshot with id: {}", value.id(), e);
          } finally {
            writeLock.unlock();
          }
        });
  }

  @Override
  public void deleteAsync(SnapshotId key) {
    Lock writeLock = fileSystemLevelLocks.getUnchecked(key).writeLock();
    writeLock.lock();
    CompletableFuture.runAsync(
        () -> {
          try {
            Files.deleteIfExists(
                Paths.get(persistenceDirectory.getPath(), getPathFromSnapshotId(key).toString()));
          } catch (IOException e) {
            log.error("Error deleting snapshot with id: {}", key, e);
          } finally {
            writeLock.unlock();
          }
        });
  }

  /**
   * If the size of the new snapshot exceeds the 90% of available space (Specified or physically
   * available), then we fetch the oldest files and delete them until we are under the limit.
   *
   * @param size the size of the new snapshot being saved to disk
   */
  private void checkSizeAndCleanupOldSnapshotsIfNeeded(int size) {
    long workingSpace =
        reservedSpace != 0
            ? Math.min(reservedSpace, persistenceDirectory.getUsableSpace())
            : persistenceDirectory.getUsableSpace();

    while (currentUsedSpace.get() >= 0.9 * workingSpace) {
      updateLastModifiedPathsIfNeeded();
      if (lastModifiedPaths.isEmpty()) {
        log.error("No more files to delete, but still over the limit");
        break;
      }

      Path path = lastModifiedPaths.poll();
      Lock writeLock = fileSystemLevelLocks.getUnchecked(getSnapshotIdFromPath(path)).writeLock();
      writeLock.lock();
      try {
        long sizeOfFile = Files.size(path);
        Files.deleteIfExists(path);
        currentUsedSpace.addAndGet(-sizeOfFile);
      } catch (IOException e) {
        log.error("Error deleting snapshot with path: {}", path, e);
      } finally {
        writeLock.unlock();
      }
    }
  }

  private void updateLastModifiedPathsIfNeeded() {
    if (lastModifiedPaths.isEmpty()) {
      try (Stream<Path> walk = Files.walk(persistenceDirectory.toPath())) {
        lastModifiedPaths.addAll(
            walk.sorted(
                    (p1, p2) -> {
                      try {
                        return Files.getLastModifiedTime(p1)
                            .compareTo(Files.getLastModifiedTime(p2));
                      } catch (IOException e) {
                        log.error(
                            "Error getting the last modified time of the file: {} or {}",
                            p1,
                            p2,
                            e);
                        // TODO: delete?
                        return 0;
                      }
                    })
                .limit(1000)
                .collect(Collectors.toList()));
      } catch (IOException e) {
        log.error("Error getting the list of files in the snapshot directory", e);
      }
    }
  }

  // TODO
  private Path getPathFromSnapshotId(SnapshotId key) {
    return Paths.get(persistenceDirectory.getPath(), key.key());
  }

  // TODO
  private SnapshotId getSnapshotIdFromPath(Path path) {
    return SnapshotId.of(path.getFileName().toString(), UUID.randomUUID());
  }

  private long getFolderSize(File folder) throws IOException {
    try (Stream<Path> walk = Files.walk(folder.toPath())) {
      return walk.mapToLong(p -> p.toFile().length()).sum();
    }
  }
}
