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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "validate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ValidateMojo extends AbstractMojo {

  @Parameter(
      defaultValue = "${project.basedir}/src/main/resources",
      property = "sourceDir",
      required = true)
  private File sourceDirectory;

  @Override
  public void execute() {
    if (!sourceDirectory.exists())
      throw new IllegalArgumentException(
          "Source directory (property 'sourceDir') does not exist: " + sourceDirectory.getPath());

    org.factcast.schema.registry.cli.Application.main(
        new String[] {"build", "-p", sourceDirectory.getAbsolutePath()});
  }
}
