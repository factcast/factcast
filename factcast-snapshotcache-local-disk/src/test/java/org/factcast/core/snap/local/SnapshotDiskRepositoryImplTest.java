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
import java.util.Optional;
import java.util.UUID;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.factcast.factus.snapshot.Snapshot;
import org.factcast.factus.snapshot.SnapshotId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SnapshotDiskRepositoryImplTest {

  private SnapshotDiskRepositoryImpl uut;

  @BeforeEach
  void setup() {
    File tmpFolder = Files.createTempDir();
    tmpFolder.deleteOnExit();

    InMemoryAndDiskSnapshotProperties properties = new InMemoryAndDiskSnapshotProperties();
    properties.setPathToSnapshots(tmpFolder.getAbsolutePath());

    uut = new SnapshotDiskRepositoryImpl(properties);
  }

  @Nested
  class WhenCrud {

    @Test
    @SneakyThrows
    void getNotFound() {
      // Get by the ID
      Optional<Snapshot> response = uut.findById(SnapshotId.of("key", UUID.randomUUID()));
      assertThat(response).isEmpty();
    }

    @Test
    @SneakyThrows
    void saveGetAndDelete() {
      final Snapshot snap =
          new Snapshot(
              SnapshotId.of("key", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);

      // Save and wait
      uut.save(snap).get();

      // Get by the ID
      Optional<Snapshot> response = uut.findById(snap.id());
      assertThat(response).isPresent();
      assertThat(response.get()).isEqualTo(snap);

      // Delete
      uut.delete(snap.id()).get();

      response = uut.findById(snap.id());
      assertThat(response).isEmpty();
    }

    @Test
    @SneakyThrows
    void testMultipleFiles() {
      final Snapshot snap1 =
          new Snapshot(
              SnapshotId.of("key1", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);
      final Snapshot snap2 =
          new Snapshot(
              SnapshotId.of("key2", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);
      final Snapshot snap3 =
          new Snapshot(
              SnapshotId.of("key3", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);

      // Save and wait
      uut.save(snap1).get();
      uut.save(snap2).get();
      uut.save(snap3).get();

      // Get by the ID
      Optional<Snapshot> response = uut.findById(snap1.id());
      assertThat(response).isPresent();
      assertThat(response.get()).isEqualTo(snap1);
      response = uut.findById(snap2.id());
      assertThat(response).isPresent();
      assertThat(response.get()).isEqualTo(snap2);
      response = uut.findById(snap3.id());
      assertThat(response).isPresent();
      assertThat(response.get()).isEqualTo(snap3);
    }
  }

  @Nested
  class WhenCleaningUp {
    @Test
    @SneakyThrows
    void testCleanup() {

      File tmpFolder = Files.createTempDir();
      tmpFolder.deleteOnExit();

      InMemoryAndDiskSnapshotProperties properties = new InMemoryAndDiskSnapshotProperties();
      properties.setPathToSnapshots(tmpFolder.getAbsolutePath());
      // The size of the 3 snapshots plus the tmp folder is more than 1000 bytes
      properties.setMaxDiskSpace(1000);

      uut = new SnapshotDiskRepositoryImpl(properties);

      final Snapshot snap1 =
          new Snapshot(
              SnapshotId.of("key1", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);
      final Snapshot snap2 =
          new Snapshot(
              SnapshotId.of("key2", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);
      final Snapshot snap3 =
          new Snapshot(
              SnapshotId.of("key3", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);

      // Save and wait
      uut.save(snap1).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(snap2).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(snap3).get();

      // After saving snap3 the cleanup should be triggered and snap1 (the oldest) should be deleted
      Awaitility.await().until(() -> !uut.findById(snap1.id()).isPresent());
    }

    @Test
    @SneakyThrows
    void testCleanup_ModifiedDateChangedInBetween() {

      File tmpFolder = Files.createTempDir();
      tmpFolder.deleteOnExit();

      InMemoryAndDiskSnapshotProperties properties = new InMemoryAndDiskSnapshotProperties();
      properties.setPathToSnapshots(tmpFolder.getAbsolutePath());
      // The size of the 3 snapshots plus the tmp folder is more than 1000 bytes
      properties.setMaxDiskSpace(1000);

      uut = new SnapshotDiskRepositoryImpl(properties);

      final Snapshot snap1 =
          new Snapshot(
              SnapshotId.of("key1", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);
      final Snapshot snap2 =
          new Snapshot(
              SnapshotId.of("key2", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);
      final Snapshot snap3 =
          new Snapshot(
              SnapshotId.of("key3", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);

      // Save and wait
      uut.save(snap1).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(snap2).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(snap3).get();

      // After saving snap3 the cleanup should be triggered and snap1 (the oldest) should be deleted
      Awaitility.await().until(() -> !uut.findById(snap1.id()).isPresent());

      // update the modified date of snap2
      uut.findById(snap2.id());

      final Snapshot snap4 =
          new Snapshot(
              SnapshotId.of("key4", UUID.randomUUID()), UUID.randomUUID(), "foo".getBytes(), false);

      // Should Trigger cleanup again and delete snap3 because 2 is now not the oldest
      uut.save(snap4).get();
      Awaitility.await().until(() -> !uut.findById(snap3.id()).isPresent());
      assertThat(uut.findById(snap2.id())).isPresent();
      assertThat(uut.findById(snap4.id())).isPresent();
    }
  }
}
