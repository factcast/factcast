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

import com.google.common.annotations.VisibleForTesting
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.registry.IndexFileCalculator
import org.factcast.schema.registry.cli.registry.getEventId
import org.factcast.schema.registry.cli.registry.getTransformationId
import org.factcast.schema.registry.cli.registry.index.FileBasedTransformation
import org.factcast.schema.registry.cli.registry.index.Index
import org.factcast.schema.registry.cli.registry.index.Schema
import org.factcast.schema.registry.cli.registry.index.SyntheticTransformation
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.utils.filterJson
import org.factcast.schema.registry.cli.utils.mapEventTransformations
import org.factcast.schema.registry.cli.utils.mapEventVersions
import org.factcast.schema.registry.cli.utils.mapEvents
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.nio.file.Path
import javax.inject.Singleton

@Singleton
class IndexFileCalculatorImpl(
    private val checksumService: ChecksumService,
    private val missingTransformationCalculator: MissingTransformationCalculator,
    private val fileSystemService: FileSystemService
) : IndexFileCalculator {
    override fun calculateIndex(project: Project, removedSchemaProps: Set<String>): Index {
        val schemas = project
            .mapEventVersions { namespace, event, version ->
                val id =
                    getEventId(namespace, event, version)

                Schema(
                    id,
                    namespace.name,
                    event.type,
                    version.version,
                    createMd5Hash(version.schemaPath, removedSchemaProps)
                )
            }

        val transformations = project
            .mapEventTransformations { namespace, event, transformation ->
                val id = getTransformationId(
                    namespace,
                    event,
                    transformation.from,
                    transformation.to
                )

                FileBasedTransformation(
                    id,
                    namespace.name,
                    event.type,
                    transformation.from,
                    transformation.to,
                    checksumService.createMd5Hash(transformation.transformationPath)
                )
            }

        val syntheticTransformations = project
            .mapEvents { namespace, event ->
                missingTransformationCalculator.calculateDowncastTransformations(event).map {
                    val (fromVersion, toVersion) = it
                    val id = getTransformationId(
                        namespace,
                        event,
                        fromVersion.version,
                        toVersion.version
                    )

                    SyntheticTransformation(
                        id,
                        namespace.name,
                        event.type,
                        fromVersion.version,
                        toVersion.version
                    )
                }
            }.flatten()

        return Index(schemas, transformations.plus(syntheticTransformations))
    }

    private fun createMd5Hash(filePath: Path, removedSchemaProps: Set<String>): String =
        if (removedSchemaProps.isNotEmpty()) createFilteredMd5Hash(filePath, removedSchemaProps)
        else checksumService.createMd5Hash(filePath)

    @VisibleForTesting
    fun createFilteredMd5Hash(filePath: Path, removedSchemaProps: Set<String>): String {
        val jsonNode = fileSystemService.readToJsonNode(filePath)
            ?: throw IllegalStateException("Loading JSON from $filePath failed")
        return checksumService.createMd5Hash(filterJson(jsonNode, removedSchemaProps))
    }
}
