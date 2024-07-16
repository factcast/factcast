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

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
class SnapshotFileHelper {
  void updateLastModified(@NonNull File persistenceFile) {
    if (persistenceFile.exists()) {
      if (!persistenceFile.setLastModified(System.currentTimeMillis())) {
        log.warn("Unable to set lastModified on {}", persistenceFile.getAbsolutePath());
      }
    }
  }

  File createFile(@NonNull File persistenceDirectory, @NonNull String key) {
    Preconditions.checkArgument(persistenceDirectory.exists());
    String hash = Hashing.sha256().hashString(key, StandardCharsets.UTF_8).toString();
    String withSlashes = addSlashes(hash);
    return new File(persistenceDirectory, withSlashes);
  }

  String addSlashes(String hash) {
    return new StringBuilder(hash)
        .insert(16, '/')
        .insert(12, '/')
        .insert(8, '/')
        .insert(4, '/')
        .toString();
  }

  long getTotalSize(File root) throws IOException {
    Preconditions.checkArgument(root.exists());
    try (Stream<Path> walk = Files.walk(root.toPath())) {
      return walk.mapToLong(p -> p.toFile().length()).sum();
    }
  }
}
