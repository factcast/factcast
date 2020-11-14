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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "build", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class BuildMojo extends AbstractBaseMojo {

  @Override
  public void execute() {
    checkSourceDirectory();

    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs();
    }

    File tempFile = whiteListFileCreator.create(includedEvents);

    org.factcast.schema.registry.cli.Application.main(
        argumentBuilder.build(sourceDirectory, outputDirectory, tempFile));
  }
}
