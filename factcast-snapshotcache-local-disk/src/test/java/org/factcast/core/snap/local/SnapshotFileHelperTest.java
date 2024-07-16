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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import com.google.common.io.Files;
import java.io.File;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
class SnapshotFileHelperTest {

  @Nested
  class WhenUpdatingLastModified {
    @Mock private @NonNull File persistenceFile;

    @Test
    void ignoresNonExistant() {
      File file = spy(new File("non-existent"));
      assertDoesNotThrow(
          () -> {
            SnapshotFileHelper.updateLastModified(file);
          });

      verify(file).exists();
      verify(file, never()).setLastModified(anyLong());
    }

    @SneakyThrows
    @Test
    void setsTS() {
      File tempDir = Files.createTempDir();
      tempDir.deleteOnExit();
      File file = spy(new File(tempDir, "existent"));
      file.deleteOnExit();
      Files.write("Foo".getBytes(), file);

      assertDoesNotThrow(
          () -> {
            SnapshotFileHelper.updateLastModified(file);
          });

      verify(file).setLastModified(anyLong());
    }
  }

  @Nested
  class WhenAddingSlashes {

    @Test
    void slicesFourDirectoriesFromPrefix() {
      String actual =
          SnapshotFileHelper.addSlashes(
              "00001111222233339999999999999999999999999999999999999999999999999999999999999");
      Assertions.assertThat(actual)
          .isEqualTo(
              "0000/1111/2222/3333/9999999999999999999999999999999999999999999999999999999999999");
    }
  }

  @Nested
  class WhenCreatingFile {

    @Test
    void checksRoot() {
      File missingRoot = new File("not-here");
      assertThatThrownBy(
              () -> {
                SnapshotFileHelper.createFile(missingRoot, "myfilename");
              })
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void happyPath() {
      File root = Files.createTempDir();
      File f = SnapshotFileHelper.createFile(root, "foobarbaz");
      File p = f.getParentFile();
      p.mkdirs();
      Assertions.assertThat(p).exists().isDirectory();
      p = p.getParentFile();
      Assertions.assertThat(p).exists().isDirectory();
      p = p.getParentFile();
      Assertions.assertThat(p).exists().isDirectory();
      p = p.getParentFile();
      Assertions.assertThat(p).exists().isDirectory();
      p = p.getParentFile();
      Assertions.assertThat(p).isEqualTo(root);
    }
  }

  @Nested
  class WhenCalculatingSize {
    @SneakyThrows
    @Test
    void name() {
      File root = Files.createTempDir();

      // some bytes for directory itself
      int OVERHEAD_FOR_DIRECTORIES = 128;
      Assertions.assertThat(SnapshotFileHelper.getTotalSize(root))
          .isLessThan(OVERHEAD_FOR_DIRECTORIES);

      File f = SnapshotFileHelper.createFile(root, "foobarbaz");
      f.getParentFile().mkdirs();
      Files.write(new byte[1024], f);
      File f2 = SnapshotFileHelper.createFile(root, "foobarbaz2");
      f2.getParentFile().mkdirs();
      Files.write(new byte[2048], f2);

      Assertions.assertThat(SnapshotFileHelper.getTotalSize(root))
          .isLessThan(3072 + OVERHEAD_FOR_DIRECTORIES)
          .isGreaterThanOrEqualTo(3072);
    }
  }
}
