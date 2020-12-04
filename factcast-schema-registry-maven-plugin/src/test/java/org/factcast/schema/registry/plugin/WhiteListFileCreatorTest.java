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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WhiteListFileCreatorTest {

  @Test
  void eventsAreWrittenToTempFile() throws IOException {
    File temporaryFile = WhiteListFileCreator.create(Arrays.asList("event 1", "event 2"));
    List<String> temporaryFileContent = Files.readAllLines(temporaryFile.toPath());

    assertEquals(2, temporaryFileContent.size());
    assertEquals("event 1", temporaryFileContent.get(0));
    assertEquals("event 2", temporaryFileContent.get(1));
  }

  @Test
  void emptyListGivesEmptyFile() throws IOException {
    File temporaryFile = WhiteListFileCreator.create(new ArrayList<>());
    List<String> temporaryFileContent = Files.readAllLines(temporaryFile.toPath());

    assertEquals(0, temporaryFileContent.size());
  }
}
