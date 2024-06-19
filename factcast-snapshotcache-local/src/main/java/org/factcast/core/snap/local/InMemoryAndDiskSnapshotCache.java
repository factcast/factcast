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
import java.time.Duration;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;

@Slf4j
public class InMemoryAndDiskSnapshotCache implements SnapshotCache {

  private static final File PERSISTENCE_DIRECTORY = new File("/tmp/snapshots/");
  private final Cache<SnapshotId, Snapshot> cache =
      CacheBuilder.newBuilder()
          .softValues()
          .removalListener(new PersistingRemovalListener())
          .expireAfterAccess(Duration.ofDays(10))
          .build();

  @Override
  public @NonNull Optional<Snapshot> getSnapshot(@NonNull SnapshotId id) {
    Optional<Snapshot> snapshotOpt = Optional.ofNullable(cache.getIfPresent(id));

    if (!snapshotOpt.isPresent()) {
      try {
        snapshotOpt = Optional.ofNullable(findValueOnDisk(id));
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
    // TODO remove from disk if exists
  }

  @Override
  public void compact(int retentionTimeInDays) {}

  private class PersistingRemovalListener implements RemovalListener<SnapshotId, Snapshot> {
    @Override
    public void onRemoval(RemovalNotification<SnapshotId, Snapshot> notification) {
      if (notification.getCause() != RemovalCause.COLLECTED) {
        try {
          persistValue(notification.getKey(), notification.getValue());
        } catch (IOException e) {
          log.error(
              String.format(
                  "Could not persist key-value: %s, %s",
                  notification.getKey(), notification.getValue()),
              e);
        }
      }
    }
  }

  private Snapshot findValueOnDisk(SnapshotId key) throws IOException, ClassNotFoundException {
    File persistenceFile = new File(PERSISTENCE_DIRECTORY, key.key());
    if (!persistenceFile.exists()) {
      return null;
    }

    if (!persistenceFile.exists()) return null;
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

  private void persistValue(SnapshotId key, Snapshot value) throws IOException {
    File persistenceFile = new File(PERSISTENCE_DIRECTORY, key.key());

    if (!persistenceFile.exists()) {
      persistenceFile.createNewFile();
    }
    try (FileOutputStream fileOutputStream = new FileOutputStream(persistenceFile)) {
      FileLock fileLock = fileOutputStream.getChannel().lock();
      try {
        try (ObjectOutputStream oos = new ObjectOutputStream(fileOutputStream)) {
          oos.writeObject(value);
          oos.flush();
        }
      } finally {
        fileLock.release();
      }
    }
  }
}
