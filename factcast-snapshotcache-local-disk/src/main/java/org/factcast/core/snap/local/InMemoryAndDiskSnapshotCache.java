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
package org.factcast.core.snap.local;

import com.google.common.cache.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;

@Slf4j
public class InMemoryAndDiskSnapshotCache implements SnapshotCache {

  private final File persistenceDirectory;
  private final Cache<SnapshotId, Snapshot> cache;

  public InMemoryAndDiskSnapshotCache(InMemoryAndDiskSnapshotProperties props) {
    persistenceDirectory = new File(System.getProperty("java.io.tmpdir") + "factcast/snapshots/");
    cache =
        CacheBuilder.newBuilder()
            .softValues()
            .removalListener(onRemoval())
            .expireAfterAccess(Duration.ofDays(props.getDeleteSnapshotStaleForDays()))
            .build();
  }

  private RemovalListener<SnapshotId, Snapshot> onRemoval() {
    return notification -> {
      if (notification.getCause() == RemovalCause.COLLECTED) {
        try {
          persistValue(notification.getKey(), notification.getValue());
        } catch (IOException e) {
          log.error(
              String.format(
                  "Could not persist to disk key-value: %s, %s",
                  notification.getKey(), notification.getValue()),
              e);
        }
      }
    };
  }

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    Optional<Snapshot> snapshotOpt = Optional.ofNullable(cache.getIfPresent(id));

    if (!snapshotOpt.isPresent()) {
      try {
        snapshotOpt = Optional.ofNullable(findValueOnDisk(id));
        snapshotOpt.ifPresent(snapshot -> cache.put(id, snapshot));
      } catch (Exception e) {
        log.error(String.format("Error retrieving snapshot with id: %s", id), e);
      }
    }

    return snapshotOpt;
  }

  @Override
  public void setSnapshot(@NonNull Snapshot snapshot) {
    cache.put(snapshot.id(), snapshot);
  }

  @Override
  public void clearSnapshot(@NonNull SnapshotId id) {
    cache.invalidate(id);
    deleteValueFromDiskIfPresent(id);
  }

  @Override
  public void compact(int retentionTimeInDays) {
    // Handled by the expiredAfterAccess of the cache
  }

  private Snapshot findValueOnDisk(SnapshotId key) throws IOException, ClassNotFoundException {
    File persistenceFile = new File(persistenceDirectory, key.key());
    if (!persistenceFile.exists()) {
      return null;
    }

    try (FileInputStream fileInputStream = new FileInputStream(persistenceFile)) {
      FileLock fileLock = fileInputStream.getChannel().lock();
      try {
        try (ObjectInputStream ois = new ObjectInputStream(fileInputStream)) {
          return (Snapshot) ois.readObject();
        }
      } finally {
        fileLock.release();
      }
    }
  }

  private void deleteValueFromDiskIfPresent(SnapshotId key) {
    try {
      Files.deleteIfExists(Paths.get(persistenceDirectory.getPath(), key.key()));
    } catch (IOException e) {
      log.error(String.format("Error deleting snapshot with id: %s", key), e);
    }
  }

  private void persistValue(SnapshotId key, Snapshot value) throws IOException {
    File persistenceFile = new File(persistenceDirectory, key.key());

    if (!persistenceFile.getParentFile().exists()) {
      persistenceFile.getParentFile().mkdirs();
    }
    if (!persistenceFile.exists()) {
      persistenceFile.createNewFile();
    }
    try (FileOutputStream fileOutputStream = new FileOutputStream(persistenceFile);
        ObjectOutputStream oos = new ObjectOutputStream(fileOutputStream)) {
      oos.writeObject(value);
      oos.flush();
    }
  }
}
