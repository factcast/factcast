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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.google.common.io.Files;
import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.awaitility.Awaitility;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.factus.serializer.SnapshotSerializerId;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
    @Captor private LogCaptor logCaptor = LogCaptor.forClass(SnapshotDiskRepositoryImpl.class);

    @BeforeEach
    void setup() {
      logCaptor.clearLogs();
    }

    @Test
    @SneakyThrows
    void getNotFound() {
      @ProjectionMetaData(name = "key", revision = 1)
      class key implements SnapshotProjection {}

      // Get by the ID
      Optional<SnapshotData> response =
          uut.findById(new SnapshotIdentifier(key.class, UUID.randomUUID()));
      assertThat(response).isEmpty();
    }

    @Test
    @SneakyThrows
    void getExceptionOnSave() {
      class key implements SnapshotProjection {}
      SnapshotIdentifier identifier = new SnapshotIdentifier(key.class, UUID.randomUUID());

      final SnapshotData snap =
          new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());
      SnapshotDiskRepositoryImpl suut = spy(uut);
      doThrow(new RuntimeException("mocked")).when(suut).triggerCleanup();

      File tmp = Files.createTempDir();
      tmp.deleteOnExit();

      assertDoesNotThrow(() -> suut.doSave(identifier, snap, new File(tmp, "test")));
    }

    @Test
    @SneakyThrows
    void getExceptionOnDelete() {
      File tmp = Files.createTempDir();
      tmp.deleteOnExit();
      new File(tmp, "test").createNewFile();

      uut.doDelete(tmp.toPath());

      List<String> errorLogs = logCaptor.getErrorLogs();
      assertThat(errorLogs).isNotEmpty().hasSize(1);
      assertThat(errorLogs.get(0)).contains("Error deleting snapshot: " + tmp.toPath());
    }

    @Test
    @SneakyThrows
    void saveGetAndDelete() {
      @ProjectionMetaData(name = "key", revision = 1)
      class key implements SnapshotProjection {}
      SnapshotIdentifier id = new SnapshotIdentifier(key.class, UUID.randomUUID());

      final SnapshotData snap =
          new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());

      // Save and wait
      uut.save(id, snap).get();

      // Get by the ID
      Optional<SnapshotData> response = uut.findById(id);
      assertThat(response).isPresent();
      assertThat(snap).isEqualTo(response.get());

      // Delete
      uut.delete(id).get();

      response = uut.findById(id);
      assertThat(response).isEmpty();
    }

    @Test
    @SneakyThrows
    void testMultipleFiles() {
      @ProjectionMetaData(name = "key1", revision = 1)
      class key1 implements SnapshotProjection {}
      @ProjectionMetaData(name = "key2", revision = 1)
      class key2 implements SnapshotProjection {}
      @ProjectionMetaData(name = "key3", revision = 1)
      class key3 implements SnapshotProjection {}
      SnapshotIdentifier id1 = new SnapshotIdentifier(key1.class, UUID.randomUUID());
      SnapshotIdentifier id2 = new SnapshotIdentifier(key2.class, UUID.randomUUID());
      SnapshotIdentifier id3 = new SnapshotIdentifier(key3.class, UUID.randomUUID());

      final SnapshotData snap1 =
          new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());
      final SnapshotData snap2 =
          new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());
      final SnapshotData snap3 =
          new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());

      // Save and wait
      uut.save(id1, snap1).get();
      uut.save(id2, snap2).get();
      uut.save(id3, snap3).get();

      // Get by the ID
      Optional<SnapshotData> response = uut.findById(id1);
      assertThat(response).isPresent();
      assertThat(snap1).isEqualTo(response.get());
      response = uut.findById(id2);
      assertThat(response).isPresent();
      assertThat(snap2).isEqualTo(response.get());
      response = uut.findById(id3);
      assertThat(response).isPresent();
      assertThat(snap3).isEqualTo(response.get());
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
      // The size of 1 snapshot is 31 bytes
      properties.setMaxDiskSpace(90);

      uut = new SnapshotDiskRepositoryImpl(properties);

      @ProjectionMetaData(name = "key1", revision = 1)
      class key1 implements SnapshotProjection {}
      @ProjectionMetaData(name = "key2", revision = 1)
      class key2 implements SnapshotProjection {}
      @ProjectionMetaData(name = "key3", revision = 1)
      class key3 implements SnapshotProjection {}
      SnapshotIdentifier id1 = new SnapshotIdentifier(key1.class, UUID.randomUUID());
      SnapshotIdentifier id2 = new SnapshotIdentifier(key2.class, UUID.randomUUID());
      SnapshotIdentifier id3 = new SnapshotIdentifier(key3.class, UUID.randomUUID());

      final SnapshotData snap =
          new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());

      // Save and wait
      uut.save(id1, snap).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(id2, snap).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(id3, snap).get();

      // After saving snap3 the cleanup should be triggered and snap1 (the oldest) should be deleted
      Awaitility.await().until(() -> uut.findById(id1).isEmpty());
    }

    @Test
    @SneakyThrows
    void testCleanup_ModifiedDateChangedInBetween() {

      File tmpFolder = Files.createTempDir();
      tmpFolder.deleteOnExit();

      InMemoryAndDiskSnapshotProperties properties = new InMemoryAndDiskSnapshotProperties();
      properties.setPathToSnapshots(tmpFolder.getAbsolutePath());
      // The size of 1 snapshot is 31 bytes
      properties.setMaxDiskSpace(90);

      uut = new SnapshotDiskRepositoryImpl(properties);

      @ProjectionMetaData(name = "key1", revision = 1)
      class key1 implements SnapshotProjection {}
      @ProjectionMetaData(name = "key2", revision = 1)
      class key2 implements SnapshotProjection {}
      @ProjectionMetaData(name = "key3", revision = 1)
      class key3 implements SnapshotProjection {}
      SnapshotIdentifier id1 = new SnapshotIdentifier(key1.class, UUID.randomUUID());
      SnapshotIdentifier id2 = new SnapshotIdentifier(key2.class, UUID.randomUUID());
      SnapshotIdentifier id3 = new SnapshotIdentifier(key3.class, UUID.randomUUID());

      final SnapshotData snap =
          new SnapshotData("foo".getBytes(), SnapshotSerializerId.of("name"), UUID.randomUUID());

      // Save and wait
      uut.save(id1, snap).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(id2, snap).get();
      // Create small difference on the modified timestamp
      Thread.sleep(50);
      uut.save(id3, snap).get();

      // After saving snap3 the cleanup should be triggered and snap1 (the oldest) should be deleted
      Awaitility.await().until(() -> uut.findById(id1).isEmpty());

      // update the modified date of snap2
      uut.findById(id2);

      @ProjectionMetaData(name = "key4", revision = 1)
      class key4 implements SnapshotProjection {}
      SnapshotIdentifier id4 = new SnapshotIdentifier(key4.class, UUID.randomUUID());

      // Should Trigger cleanup again and delete snap3 because 2 is now not the oldest
      uut.save(id4, snap).get();
      Awaitility.await().until(() -> uut.findById(id3).isEmpty());
      assertThat(uut.findById(id2)).isPresent();
      assertThat(uut.findById(id4)).isPresent();
    }
  }
}
