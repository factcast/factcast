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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the command line arguments for the schema registry CLI */
public class CliArgumentBuilder {

  // Utility class
  private CliArgumentBuilder() {}

  public static String[] build(String command, File sourceDirectory, List<String> includedEvents) {
    return build(command, sourceDirectory, null, includedEvents, false, new HashSet<>());
  }

  public static String[] build(
      String command,
      File sourceDirectory,
      File outputDirectory,
      List<String> includedEvents,
      boolean schemaStripTitles,
      Set<String> stripSchemaProperties) {
    List<String> argumentList = new ArrayList<>();
    argumentList.add(command);

    argumentList.add("-p");
    argumentList.add(sourceDirectory.getAbsolutePath());

    if (outputDirectory != null) {
      argumentList.add("-o");
      argumentList.add(outputDirectory.getAbsolutePath());
    }

    if (!includedEvents.isEmpty()) {
      File tempFile = WhiteListFileCreator.create(includedEvents);
      argumentList.add("-w");
      argumentList.add(tempFile.getAbsolutePath());
    }

    if (schemaStripTitles) {
      argumentList.add("-s");
    }

    if (!stripSchemaProperties.isEmpty()) {
      argumentList.add("--schema-remove-fields");
      argumentList.add(String.join(",", stripSchemaProperties));
    }

    String[] argumentListArr = new String[argumentList.size()];
    return argumentList.toArray(argumentListArr);
  }
}
