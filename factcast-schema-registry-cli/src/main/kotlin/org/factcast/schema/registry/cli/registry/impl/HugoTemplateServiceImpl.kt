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

import javax.inject.Singleton
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.registry.TemplateService
import org.factcast.schema.registry.cli.registry.templates.data.ChangelogEntry
import org.factcast.schema.registry.cli.registry.templates.data.EventTemplateData
import org.factcast.schema.registry.cli.registry.templates.data.ExampleTemplateData
import org.factcast.schema.registry.cli.registry.templates.data.HomeTemplateData
import org.factcast.schema.registry.cli.registry.templates.data.NamespaceTemplateData
import org.factcast.schema.registry.cli.registry.templates.data.TransformationData
import org.factcast.schema.registry.cli.registry.templates.data.TransformationTemplateData
import org.factcast.schema.registry.cli.registry.templates.data.VersionTemplateData
import org.factcast.schema.registry.cli.registry.templates.eventTemplate
import org.factcast.schema.registry.cli.registry.templates.homeTemplate
import org.factcast.schema.registry.cli.registry.templates.namespaceTemplate
import org.factcast.schema.registry.cli.registry.templates.transformationTemplate
import org.factcast.schema.registry.cli.registry.templates.versionTemplate

@Singleton
class HugoTemplateServiceImpl(private val fileSystemService: FileSystemService) : TemplateService {
    override fun loadHomeTemplate(project: Project): String {
        val description =
            if (project.description != null) fileSystemService.readToString(project.description.toFile()) else null

        return homeTemplate(HomeTemplateData(description))
    }

    override fun loadNamespaceTemplate(namespace: Namespace): String {
        val data = NamespaceTemplateData(
            namespace.name,
            fileSystemService.readToString(namespace.descriptionPath.toFile())
        )

        return namespaceTemplate(data)
    }

    override fun loadEventTemplate(namespace: Namespace, event: Event): String {
        val description = fileSystemService.readToString(event.descriptionPath.toFile())
        val latestVersion = event.versions.maxByOrNull { it.version }!!

        val latestVersionData =
            getVersionTemplateData(namespace, event, latestVersion)

        val changelog = event.versions
            .sortedByDescending { it.version }
            .map {
                ChangelogEntry(it.version, fileSystemService.readToString(it.descriptionPath.toFile()))
            }

        val data = EventTemplateData(event.type, namespace.name, description, latestVersionData, changelog)

        return eventTemplate(data)
    }

    override fun loadVersionTemplate(namespace: Namespace, event: Event, version: Version): String {
        val data = getVersionTemplateData(namespace, event, version)

        return versionTemplate(data)
    }

    override fun loadTransformationsTemplate(namespace: Namespace, event: Event): String {
        val transformations = event.transformations.map {
            TransformationData(it.from, it.to, fileSystemService.readToString(it.transformationPath.toFile()))
        }
        val maxVersion = event.versions.maxByOrNull { it.version }!!

        val data = TransformationTemplateData(namespace.name, event.type, maxVersion.version, transformations)

        return transformationTemplate(data)
    }

    private fun getVersionTemplateData(
        namespace: Namespace,
        event: Event,
        version: Version
    ): VersionTemplateData {
        val schema = fileSystemService.readToString(version.schemaPath.toFile())
        val versionChangelog = fileSystemService.readToString(version.descriptionPath.toFile())

        val examples = version.examples.map {
            val json = fileSystemService.readToString(it.exampleFilePath.toFile())

            ExampleTemplateData(it.name, json)
        }

        return VersionTemplateData(version.version, namespace.name, event.type, schema, versionChangelog, examples)
    }
}
