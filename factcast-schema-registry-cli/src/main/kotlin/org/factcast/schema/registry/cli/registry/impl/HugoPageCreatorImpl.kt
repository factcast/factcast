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

import java.nio.file.Path
import java.nio.file.Paths
import jakarta.inject.Singleton
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.registry.StaticPageCreator
import org.factcast.schema.registry.cli.registry.TemplateService

@Singleton
class HugoPageCreatorImpl(
    private val fileSystemService: FileSystemService,
    private val templateService: TemplateService
) : StaticPageCreator {
    override fun createPage(outputPath: Path, project: Project) {
        copySite(outputPath)

        val contentBase = outputPath.resolve("content")
        createLandingPage(contentBase, project)
        createDynamicPages(contentBase, project.namespaces)
    }

    private fun createLandingPage(contentBase: Path, project: Project) {
        val descriptionPath = contentBase.resolve("_index.md")

        val homeTemplate = templateService.loadHomeTemplate(project)
        fileSystemService.writeToFile(descriptionPath.toFile(), homeTemplate)
    }

    private fun createDynamicPages(contentBase: Path, namespaces: List<Namespace>) {
        namespaces.forEach {
            createNamespace(contentBase, it)
        }
    }

    private fun createNamespace(outputPath: Path, namespace: Namespace) {
        val nsPath = outputPath.resolve(namespace.name)
        val descriptionPath = nsPath.resolve("_index.md")

        val namespacePage = templateService.loadNamespaceTemplate(namespace)
        fileSystemService.writeToFile(descriptionPath.toFile(), namespacePage)

        namespace.events.forEach {
            createEvent(nsPath, namespace, it)
        }
    }

    private fun createEvent(nsPath: Path, namespace: Namespace, event: Event) {
        val eventPath = nsPath.resolve(event.type)
        val descriptionPath = eventPath.resolve("_index.md")

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

        val filePath = eventPath.resolve(Paths.get("transformations", "_index.md"))
        val template = templateService.loadTransformationsTemplate(namespace, event)

        fileSystemService.writeToFile(filePath.toFile(), template)
    }

    private fun createVersion(eventPath: Path, namespace: Namespace, event: Event, version: Version) {
        val versionPath = eventPath.resolve(version.version.toString())
        val descriptionPath = versionPath.resolve("_index.md")

        val versionTemplate = templateService.loadVersionTemplate(namespace, event, version)
        fileSystemService.writeToFile(
            descriptionPath.toFile(),
            versionTemplate
        )
    }

    private fun copySite(basePath: Path) {
        fileSystemService.copyFromClasspath("site", basePath)
    }
}
