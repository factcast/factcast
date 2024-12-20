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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Files;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.factcast.core.snap.local.OldestModifiedFileProvider.PathWithLastModifiedDate;
import org.factcast.core.snap.local.utils.PathWithException;
import org.junit.jupiter.api.Test;

class OldestModifiedFileProviderTest {

  @Test
  void persistenceDirectoryDoesntExist() {
    OldestModifiedFileProvider provider = new OldestModifiedFileProvider(new File("non-existent"));

    assertThatThrownBy(provider::get)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Persistence directory doesn't exist or is not a directory");
  }

  @Test
  @SneakyThrows
  void testOrderOfFiles() {
    File tempDir = Files.createTempDir();
    tempDir.deleteOnExit();
    File innerDir = new File(tempDir, "inner/other");
    innerDir.mkdirs();
    innerDir.deleteOnExit();

    File a = new File(tempDir, "a");
    a.createNewFile();
    a.setLastModified(1000);
    File b = new File(tempDir, "b");
    b.createNewFile();
    b.setLastModified(500);
    File c = new File(innerDir, "c");
    c.createNewFile();
    c.setLastModified(0);

    OldestModifiedFileProvider provider = new OldestModifiedFileProvider(tempDir);

    PathWithLastModifiedDate fileWithDate = provider.get();
    assertThat(fileWithDate.path().toString()).endsWith("/c");
    fileWithDate.path().toFile().delete();

    fileWithDate = provider.get();
    assertThat(fileWithDate.path().toString()).endsWith("/b");
    fileWithDate.path().toFile().delete();

    fileWithDate = provider.get();
    assertThat(fileWithDate.path().toString()).endsWith("/a");
    fileWithDate.path().toFile().delete();

    fileWithDate = provider.get();
    assertThat(fileWithDate).isNull();
  }

  @Test
  @SneakyThrows
  void testOrderOfFilesWithIOExceptionDefault() {
    File tempDir = Files.createTempDir();
    tempDir.deleteOnExit();
    File innerDir = new File(tempDir, "inner/other");
    innerDir.mkdirs();
    innerDir.deleteOnExit();

    File a = new File(tempDir, "a");
    a.createNewFile();
    a.setLastModified(1000);
    File b = new File(tempDir, "b");
    b.createNewFile();
    b.setLastModified(500);
    File c = new File(innerDir, "c");
    c.createNewFile();
    c.setLastModified(1);

    OldestModifiedFileProvider provider = new OldestModifiedFileProvider(tempDir);

    PathWithException pathWithException = new PathWithException("/b");
    Stream<Path> walk = Stream.of(a.toPath(), pathWithException, c.toPath());

    List<PathWithLastModifiedDate> filesWithDates = provider.getTheLastModifiedFiles(walk);
    assertThat(filesWithDates.get(0).path()).isEqualTo(pathWithException);
    filesWithDates.get(0).path().toFile().delete();
    assertThat(filesWithDates.get(1).path().toString()).endsWith("/c");
    filesWithDates.get(1).path().toFile().delete();
    assertThat(filesWithDates.get(2).path().toString()).endsWith("/a");
    filesWithDates.get(2).path().toFile().delete();
  }

  @Test
  @SneakyThrows
  void testDateChangedInTheMiddle() {
    File tempDir = Files.createTempDir();
    tempDir.deleteOnExit();

    File a = new File(tempDir, "a");
    a.createNewFile();
    a.setLastModified(1000);
    File b = new File(tempDir, "b");
    b.createNewFile();
    b.setLastModified(500);
    File c = new File(tempDir, "c");
    c.createNewFile();
    c.setLastModified(0);

    OldestModifiedFileProvider provider = new OldestModifiedFileProvider(tempDir);

    PathWithLastModifiedDate fileWithDate = provider.get();
    assertThat(fileWithDate.path().toString()).endsWith("/c");
    // We assume the date changed in the middle, so we dont delete it and continue
    c.setLastModified(1);

    fileWithDate = provider.get();
    assertThat(fileWithDate.path().toString()).endsWith("/b");
    fileWithDate.path().toFile().delete();

    fileWithDate = provider.get();
    assertThat(fileWithDate.path().toString()).endsWith("/a");
    fileWithDate.path().toFile().delete();

    // Now the list gets re populated and we get the new order
    fileWithDate = provider.get();
    assertThat(fileWithDate.path().toString()).endsWith("/c");
    fileWithDate.path().toFile().delete();

    fileWithDate = provider.get();
    assertThat(fileWithDate).isNull();
  }
}
