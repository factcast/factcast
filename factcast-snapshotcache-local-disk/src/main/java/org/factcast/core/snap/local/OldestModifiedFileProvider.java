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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class OldestModifiedFileProvider
    implements Supplier<OldestModifiedFileProvider.PathWithLastModifiedDate> {
  private final File persistenceDirectory;
  private final Deque<PathWithLastModifiedDate> lastModifiedPaths = new LinkedList<>();

  @Override
  public PathWithLastModifiedDate get() {
    Preconditions.checkArgument(
        persistenceDirectory.exists() && persistenceDirectory.isDirectory(),
        "Persistence directory doesn't exist or is not a directory");
    if (lastModifiedPaths.isEmpty()) {
      try (Stream<Path> walk = Files.walk(persistenceDirectory.toPath())) {
        lastModifiedPaths.addAll(getTheLastModifiedFiles(walk));
      } catch (IOException e) {
        log.error("Error getting the list of files in the snapshot directory", e);
      }
    }

    return lastModifiedPaths.poll();
  }

  @VisibleForTesting
  protected List<PathWithLastModifiedDate> getTheLastModifiedFiles(Stream<Path> walk) {
    return walk.filter(p -> !p.toFile().isDirectory())
        .map(
            p -> {
              try {
                return PathWithLastModifiedDate.of(p, Files.getLastModifiedTime(p));
              } catch (IOException e) {
                return PathWithLastModifiedDate.of(p, FileTime.from(0L, TimeUnit.MILLISECONDS));
              }
            })
        .sorted(Comparator.comparing(PathWithLastModifiedDate::lastAccessTime))
        .limit(1000)
        .collect(Collectors.toList());
  }

  @Value(staticConstructor = "of")
  public static class PathWithLastModifiedDate {
    Path path;
    FileTime lastAccessTime;

    PathWithLastModifiedDate(Path path, FileTime lastAccessTime) {
      this.path = path;
      this.lastAccessTime = lastAccessTime;
    }
  }
}
