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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.io.Files;
import java.io.File;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotFileHelperTest {

  @Nested
  class WhenUpdatingLastModified {
    @Mock private @NonNull File persistenceFile;

    @Test
    void ignoresNonExistant() {
      File file = spy(new File("non-existant"));
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
      File file = spy(new File(tempDir, "existant"));
      file.deleteOnExit();
      Files.write("Foo".getBytes(), file);

      assertDoesNotThrow(
          () -> {
            SnapshotFileHelper.updateLastModified(file);
          });

      verify(file).setLastModified(anyLong());
    }
  }
}
