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
package org.factcast.schema.registry.cli.project

import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.project.structure.TransformationFolder
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Singleton

const val VERSIONS_FOLDER = "versions"
const val TRANSFORMATIONS_FOLDER = "transformations"
const val EXAMPLES_FOLDER = "examples"
const val DESCRIPTION_FILE = "index.md"
const val TRANSFORMATION_FILE = "transform.js"
const val SCHEMA_FILE = "schema.json"

@Singleton
class ProjectServiceImpl(private val fileSystem: FileSystemService) : ProjectService {
    override fun detectProject(basePath: Path): ProjectFolder {
        return ProjectFolder(
            basePath,
            fileExists(basePath, DESCRIPTION_FILE),
            fileSystem
                .listDirectories(basePath)
                .map { path ->
                    NamespaceFolder(
                        path,
                        loadEvents(path),
                        fileExists(path, DESCRIPTION_FILE)
                    )
                }

        )
    }

    private fun loadEvents(namespaceBasePath: Path) =
        try {
            fileSystem.listDirectories(namespaceBasePath)
                .map { x ->
                    EventFolder(
                        x,
                        loadVersions(x),
                        fileExists(x, DESCRIPTION_FILE),
                        loadTransformations(x)
                    )
                }
        } catch (e: NoSuchFileException) {
            emptyList<EventFolder>()
        }

    private fun loadTransformations(eventBasePath: Path): List<TransformationFolder> =
        try {
            val transformationsFolder = Paths.get(
                eventBasePath.toString(),
                TRANSFORMATIONS_FOLDER
            )

            fileSystem.listDirectories(transformationsFolder)
                .map {
                    TransformationFolder(
                        it,
                        fileExists(it, TRANSFORMATION_FILE)
                    )
                }
        } catch (e: NoSuchFileException) {
            emptyList()
        }

    private fun loadVersions(eventBasePath: Path) =
        try {
            val versionFolderPath = Paths.get(
                eventBasePath.toString(),
                VERSIONS_FOLDER
            )

            fileSystem
                .listDirectories(versionFolderPath)
                .map {
                    EventVersionFolder(
                        it,
                        fileExists(it, SCHEMA_FILE),
                        fileExists(it, DESCRIPTION_FILE),
                        loadExamples(it)
                    )
                }
        } catch (e: NoSuchFileException) {
            emptyList<EventVersionFolder>()
        }

    private fun loadExamples(eventBasePath: Path) =
        try {
            fileSystem.listFiles(
                Paths.get(
                    eventBasePath.toString(),
                    EXAMPLES_FOLDER
                )
            )
        } catch (e: NoSuchFileException) {
            emptyList<Path>()
        }

    private fun fileExists(folder: Path, fileName: String): Path? {
        val filePath = Paths.get(
            folder.toString(),
            fileName
        )

        return if (fileSystem.exists(filePath)) {
            filePath
        } else {
            null
        }
    }
}