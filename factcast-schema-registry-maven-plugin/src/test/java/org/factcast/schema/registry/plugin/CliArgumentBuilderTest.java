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
import static org.mockito.Mockito.*;

import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliArgumentBuilderTest {

  private File sourceDir;
  private File emptyWhiteList;
  private File whiteList;
  private File outputDir;

  @BeforeEach
  public void setup() {
    sourceDir = mock(File.class);
    when(sourceDir.getAbsolutePath()).thenReturn("foo");

    outputDir = mock(File.class);
    when(outputDir.getAbsolutePath()).thenReturn("bazz");

    whiteList = mock(File.class);
    when(whiteList.getAbsolutePath()).thenReturn("bar");
    when(whiteList.length()).thenReturn(Long.valueOf(1));

    emptyWhiteList = mock(File.class);
    when(emptyWhiteList.getAbsolutePath()).thenReturn("bar");
    when(emptyWhiteList.length()).thenReturn(Long.valueOf(0));
  }

  @Test
  void withEmptyWhiteList() {
    String[] builder = CliArgumentBuilder.build("build", sourceDir, Collections.emptyList());

    assertEquals(3, builder.length);
    assertEquals("build", builder[0]);
    assertEquals("-p", builder[1]);
    assertEquals("foo", builder[2]);
  }

  @Test
  void withWhiteList() throws IOException {
    String[] builder = CliArgumentBuilder.build("validate", sourceDir, Arrays.asList("bar"));

    assertEquals(5, builder.length);
    assertEquals("-w", builder[3]);
    assertTrue(Files.readAllLines(Paths.get(builder[4])).contains("bar"));
  }

  @Test
  void withOutputDirAndEmptyWhiteList() {
    String[] builder =
        CliArgumentBuilder.build(
            "build", sourceDir, outputDir, Collections.emptyList(), false, Sets.newHashSet());

    assertEquals(5, builder.length);
    assertEquals("-o", builder[3]);
    assertEquals("bazz", builder[4]);
  }

  @Test
  void withAllArguments() throws IOException {
    String[] builder =
        CliArgumentBuilder.build(
            "build",
            sourceDir,
            outputDir,
            Arrays.asList("bar"),
            true,
            Sets.newHashSet("description", "example"));

    assertEquals(10, builder.length);
    assertEquals("-o", builder[3]);
    assertEquals("bazz", builder[4]);
    assertEquals("-w", builder[5]);
    assertTrue(Files.readAllLines(Paths.get(builder[6])).contains("bar"));
    assertEquals("-s", builder[7]);
    assertEquals("--schema-remove-fields", builder[8]);
    assertEquals("description,example", builder[9]);
  }
}
