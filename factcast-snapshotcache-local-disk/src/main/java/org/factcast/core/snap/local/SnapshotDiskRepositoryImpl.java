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

import static java.io.File.separator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.snap.local.OldestModifiedFileProvider.PathWithLastModifiedDate;
import org.factcast.core.util.ExceptionHelper;

@Slf4j
public class SnapshotDiskRepositoryImpl implements SnapshotDiskRepository {
  public static final String INNER_PATH =
      separator + "factcast" + separator + "snapshots" + separator;

  @Getter final File persistenceDirectory;

  private final long threshold;
  private final AtomicLong currentUsedSpace;
  private final OldestModifiedFileProvider oldestFileProvider;
  private final FileLevelLocking locking;

  public SnapshotDiskRepositoryImpl(@NonNull InMemoryAndDiskSnapshotProperties properties) {
    File cacheRoot = new File(properties.getPathToSnapshots());
    Preconditions.checkState(
        cacheRoot.exists() && cacheRoot.isDirectory(),
        cacheRoot.getAbsolutePath() + " must exist and be a directory");
    this.persistenceDirectory = new File(cacheRoot, INNER_PATH);
    persistenceDirectory.mkdirs();
    this.oldestFileProvider = new OldestModifiedFileProvider(this.persistenceDirectory);
    this.threshold = (long) (properties.getMaxDiskSpace() * 0.9);
    this.locking = new FileLevelLocking();

    // Set the current used space at startup
    try {
      currentUsedSpace = new AtomicLong(SnapshotFileHelper.getTotalSize(persistenceDirectory));
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

  public CompletableFuture<Void> save(Snapshot value) {
    File target = SnapshotFileHelper.createFile(persistenceDirectory, value.id().key());
    return locking.withWriteLockOnAsync(target, () -> doSave(value, target));
  }

  @VisibleForTesting
  protected void doSave(Snapshot value, File target) {
    try {
      target.getParentFile().mkdirs();
      long bytes =
          SnapshotSerializationHelper.serializeTo(value, Files.newOutputStream(target.toPath()));
      this.currentUsedSpace.addAndGet(bytes);
      triggerCleanup();
    } catch (Exception e) {
      log.error("Error saving snapshot with id: {}", value.id(), e);
    }
  }

  public CompletableFuture<Void> delete(SnapshotId id) {
    File target = SnapshotFileHelper.createFile(persistenceDirectory, id.key());
    return locking.withWriteLockOnAsync(target, () -> doDelete(target.toPath()));
  }

  @VisibleForTesting
  protected void doDelete(Path path) {
    try {
      if (Files.exists(path)) {
        long sizeOfFile = Files.size(path);
        Files.deleteIfExists(path);
        currentUsedSpace.addAndGet(-sizeOfFile);
      }
    } catch (IOException e) {
      log.error("Error deleting snapshot: {}", path, e);
    }
  }

  @SneakyThrows
  @Override
  public Optional<Snapshot> findById(SnapshotId id) {
    File persistenceFile = SnapshotFileHelper.createFile(persistenceDirectory, id.key());

    return locking.withReadLockOn(
        persistenceFile,
        () -> {
          if (!persistenceFile.exists()) {
            return Optional.empty();
          } else {
            SnapshotFileHelper.updateLastModified(persistenceFile);

            Path path = persistenceFile.toPath();
            try (InputStream fis = Files.newInputStream(path);
                InputStream bis = new BufferedInputStream(fis);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
              Snapshot snapshot = (Snapshot) ois.readObject();
              return Optional.of(snapshot);
            } catch (IOException e) {
              log.error("Error reading snapshot with id: {} and path: {}", id, path, e);
              return Optional.empty();
            } catch (ClassNotFoundException e) {
              doDelete(path);
              log.error("Error deserializing snapshot with id: {} and path: {}", id, path, e);
              return Optional.empty();
            }
          }
        });
  }

  /**
   * If the size of the new snapshot exceeds the 90% of configured max space, then we fetch the
   * oldest files and delete them until we are under the limit.
   */
  @VisibleForTesting
  protected void triggerCleanup() {
    if (needsCleanup()) CompletableFuture.runAsync(this::cleanup);
  }

  @VisibleForTesting
  boolean needsCleanup() {
    return threshold != 0 && currentUsedSpace.get() >= threshold;
  }

  @SneakyThrows
  private synchronized void cleanup() {
    while (currentUsedSpace.get() >= threshold) {
      PathWithLastModifiedDate oldestFile = oldestFileProvider.get();

      while (oldestFile != null
          && !Files.getLastModifiedTime(oldestFile.path()).equals(oldestFile.lastAccessTime())) {
        oldestFile = oldestFileProvider.get();
      }

      if (oldestFile == null) {
        log.error("No more Snapshots to delete from Disk, but still over the limit");
        return;
      }

      Path path = oldestFile.path();
      doDelete(path);
    }
  }
}
