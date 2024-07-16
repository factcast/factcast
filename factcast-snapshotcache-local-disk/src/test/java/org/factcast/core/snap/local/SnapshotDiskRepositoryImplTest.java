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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Files;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import org.factcast.factus.snapshot.Snapshot;
import org.factcast.factus.snapshot.SnapshotId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SnapshotDiskRepositoryImplTest {
  private static final long SLEEP_TIME = 5000;

  @Nested
  class WhenLockingThroughWrite {
    @Test
    @SneakyThrows
    void blocksReadWhenLocked() {
      AtomicBoolean finishedSleeping = new AtomicBoolean(false);

      File tmpFolder = Files.createTempDir();

      InMemoryAndDiskSnapshotProperties properties = new InMemoryAndDiskSnapshotProperties();
      properties.setPathToSnapshots(tmpFolder.getAbsolutePath());

      SnapshotDiskRepositoryImpl uut =
          new SnapshotDiskRepositoryImpl(properties) {

            @SneakyThrows
            @Override
            protected void doSave(Snapshot value, File target) {
              Thread.sleep(SLEEP_TIME);
              finishedSleeping.set(true);
              super.doSave(value, target);
            }
          };

      final Snapshot snap =
          new Snapshot(
              SnapshotId.of("key", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);

      CompletableFuture.runAsync(() -> uut.save(snap)).get();

      // Test that read is unblocked (how)
      uut.findById(snap.id());
      assertThat(finishedSleeping.get()).isTrue();

      // Assert it got unblocked after the time and we get the snapshot
    }
  }
}
