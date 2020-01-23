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
package org.factcast.schema.registry.cli.registry

// import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.ObjectMapper
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.registry.index.FileBasedTransformation
import org.factcast.schema.registry.cli.registry.index.IndexFile
import org.factcast.schema.registry.cli.registry.index.Schema
import org.factcast.schema.registry.cli.registry.index.SyntheticTransformation
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.utils.mapEventTransformations
import org.factcast.schema.registry.cli.utils.mapEventVersions
import org.factcast.schema.registry.cli.utils.mapEvents
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Singleton

@Singleton
class RegistryServiceImpl(
    private val fileSystemService: FileSystemService,
    private val om: ObjectMapper,
    private val templateService: TemplateService,
    private val missingTransformationCalculator: MissingTransformationCalculator,
    private val checksumService: ChecksumService
) : RegistryService {
    override fun createRegistry(outputPath: Path, project: Project) {
        fileSystemService.ensureDirectories(outputPath)

        copySite(outputPath)

        val contentBase = Paths.get(outputPath.toString(), "content")
        createLandingPage(contentBase, project)
        createDynamicPages(contentBase, project.namespaces)

        val indexPath = Paths.get(outputPath.toString(), "static", "registry")
        createIndexFile(indexPath, project)
    }

    fun createLandingPage(contentBase: Path, project: Project) {
        val descriptionPath = Paths.get(contentBase.toString(), "_index.md")

        val homeTemplate = templateService.loadHomeTemplate(project)
        fileSystemService.writeToFile(descriptionPath.toFile(), homeTemplate)
    }

    fun createDynamicPages(contentBase: Path, namespaces: List<Namespace>) {
        namespaces.forEach {
            createNamespace(contentBase, it)
        }
    }

    fun createNamespace(outputPath: Path, namespace: Namespace) {
        val nsPath = Paths.get(outputPath.toString(), namespace.name)
        val descriptionPath = Paths.get(nsPath.toString(), "_index.md")

        val namespacePage = templateService.loadNamespaceTemplate(namespace)
        fileSystemService.writeToFile(descriptionPath.toFile(), namespacePage)

        namespace.events.forEach {
            createEvent(nsPath, namespace, it)
        }
    }

    fun createEvent(nsPath: Path, namespace: Namespace, event: Event) {
        val eventPath = Paths.get(nsPath.toString(), event.type)
        val descriptionPath = Paths.get(eventPath.toString(), "_index.md")

        val eventTemplate = templateService.loadEventTemplate(namespace, event)
        fileSystemService.writeToFile(descriptionPath.toFile(), eventTemplate)

        createTransformationsPage(eventPath, namespace, event)

        event.versions.forEach {
            createVersion(eventPath, namespace, event, it)
        }
    }

    private fun createTransformationsPage(eventPath: Path, namespace: Namespace, event: Event) {
        if (event.transformations.isEmpty()) {
            return
        }

        val filePath = Paths.get(eventPath.toString(), "transformations", "_index.md")
        val template = templateService.loadTransformationsTemplate(namespace, event)

        fileSystemService.writeToFile(filePath.toFile(), template)
    }

    fun createVersion(eventPath: Path, namespace: Namespace, event: Event, version: Version) {
        val versionPath = Paths.get(eventPath.toString(), version.version.toString())
        val descriptionPath = Paths.get(versionPath.toString(), "_index.md")

        val versionTemplate = templateService.loadVersionTemplate(namespace, event, version)
        fileSystemService.writeToFile(
            descriptionPath.toFile(),
            versionTemplate
        )
    }

    fun copySite(basePath: Path) {
        val sitePath = Paths.get("src", "main", "resources", "site")
        fileSystemService.copyDirectory(sitePath, basePath)
    }

    fun createIndexFile(outputPath: Path, project: Project) {
        val schemas = project
            .mapEventVersions { namespace, event, version ->
                copySchemaJson(outputPath, namespace, event, version)

                val id = getEventId(namespace, event, version)

                Schema(
                    id,
                    namespace.name,
                    event.type,
                    version.version,
                    checksumService.createMd5Hash(version.schemaPath)
                )
            }

        val transformations = project.mapEventTransformations { namespace, event, transformation ->
            copyTransformation(outputPath, namespace, event, transformation)
            val id = getTransformationId(namespace, event, transformation.from, transformation.to)

            FileBasedTransformation(
                id,
                namespace.name,
                event.type,
                transformation.from,
                transformation.to,
                checksumService.createMd5Hash(transformation.transformationPath)
            )
        }

        val syntheticTransformations = project.mapEvents { namespace, event ->
            missingTransformationCalculator.calculateDowncastTransformations(event).map {
                val(fromVersion, toVersion) = it
                val id = getTransformationId(namespace, event, fromVersion.version, toVersion.version)

                SyntheticTransformation(
                    id,
                    namespace.name,
                    event.type,
                    fromVersion.version,
                    toVersion.version
                )
            }
        }.flatten()

        val index = IndexFile(schemas, transformations.plus(syntheticTransformations))
        om.writeValue(Paths.get(outputPath.toString(), "index.json").toFile(), index)
    }

    private fun copyTransformation(
        outputPath: Path,
        namespace: Namespace,
        event: Event,
        transformation: Transformation
    ) {
        val transformationPath = Paths.get(outputPath.toString(), getTransformationId(namespace, event, transformation.from, transformation.to))

        fileSystemService.copyFile(transformation.transformationPath.toFile(), transformationPath.toFile())
    }

    fun copySchemaJson(
        registryPath: Path,
        namespace: Namespace,
        event: Event,
        version: Version
    ) {
        val outputPath = Paths.get(registryPath.toString(), getEventId(namespace, event, version))

        fileSystemService.copyFile(version.schemaPath.toFile(), outputPath.toFile())
    }
}