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
package org.factcast.schema.registry.cli.registry.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import java.nio.file.Path
import javax.inject.Singleton
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.registry.FactcastIndexCreator
import org.factcast.schema.registry.cli.registry.IndexFileCalculator
import org.factcast.schema.registry.cli.registry.getEventId
import org.factcast.schema.registry.cli.registry.getTransformationId
import org.factcast.schema.registry.cli.utils.mapEventTransformations
import org.factcast.schema.registry.cli.utils.mapEventVersions

@Singleton
class FactcastIndexCreatorImpl(
    private val fileSystemService: FileSystemService,
    private val om: ObjectMapper,
    private val indexFileCalculator: IndexFileCalculator
) : FactcastIndexCreator {
    override fun createFactcastIndex(contentBase: Path, project: Project) {
        createIndexFile(contentBase, project)
        copySchemes(contentBase, project)
        copyTransformations(contentBase, project)
    }

    @VisibleForTesting
    fun copyTransformations(registryPath: Path, project: Project) {
        project.mapEventTransformations { namespace, event, transformation ->
            val transformationPath = registryPath.resolve(
                getTransformationId(namespace, event, transformation.from, transformation.to)
            )

            fileSystemService.copyFile(
                transformation.transformationPath.toFile(),
                transformationPath.toFile()
            )
        }
    }

    @VisibleForTesting
    fun copySchemes(registryPath: Path, project: Project) {
        project.mapEventVersions { namespace, event, version ->
            val outputPath = registryPath.resolve(getEventId(namespace, event, version))

            fileSystemService.copyFile(version.schemaPath.toFile(), outputPath.toFile())
        }
    }

    @VisibleForTesting
    fun createIndexFile(contentBase: Path, project: Project) {
        val index = indexFileCalculator.calculateIndex(project)
        val path = contentBase.resolve("index.json")

        fileSystemService.ensureDirectories(contentBase)
        om.writeValue(path.toFile(), index)
    }
}
