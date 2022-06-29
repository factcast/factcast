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
package org.factcast.schema.registry.plugin.mojo;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractBaseMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project.build.directory}/registry")
  protected File outputDirectory;

  @Parameter(defaultValue = "${project.basedir}/src/main/resources")
  protected File sourceDirectory;

  @Parameter(property = "includedEvents")
  protected List<String> includedEvents;

  @Parameter protected boolean schemaStripTitles;

  @Parameter(property = "removeSchemaFields")
  protected Set<String> removeSchemaFields = new HashSet<>();

  protected void checkSourceDirectory() {
    if (!sourceDirectory.exists())
      throw new IllegalArgumentException(
          "Source directory (property 'sourceDirectory') does not exist: "
              + sourceDirectory.getPath());
  }
}
