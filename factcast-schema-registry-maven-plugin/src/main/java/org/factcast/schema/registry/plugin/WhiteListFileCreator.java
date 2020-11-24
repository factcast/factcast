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
package org.factcast.schema.registry.plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import lombok.SneakyThrows;

/**
 * Gets a list of to be white listed events and creates a temporary file with the events on
 * individual lines.
 */
public class WhiteListFileCreator {

  private WhiteListFileCreator() {}

  public static File create(List<String> includedEvents) {
    File tempFile = createTempFile();
    includedEvents.forEach(event -> appendToFile(tempFile, event));
    return tempFile;
  }

  @SneakyThrows
  private static File createTempFile() {
    File tempFile = Files.createTempFile("temp-event-whitelist", ".txt").toFile();
    tempFile.deleteOnExit();
    return tempFile;
  }

  @SneakyThrows
  private static void appendToFile(File file, String line) {
    Files.write(file.toPath(), (line + "\r\n").getBytes(), StandardOpenOption.APPEND);
  }
}
