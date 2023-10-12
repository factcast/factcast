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
package org.factcast.schema.registry.cli.project.impl

import com.google.common.annotations.VisibleForTesting
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import jakarta.inject.Singleton
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.ProjectService
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.project.structure.TransformationFolder
import org.factcast.schema.registry.cli.project.structure.log
import org.factcast.schema.registry.cli.whitelistfilter.WhiteListFilterService

const val VERSIONS_FOLDER = "versions"
const val TRANSFORMATIONS_FOLDER = "transformations"
const val EXAMPLES_FOLDER = "examples"
const val DESCRIPTION_FILE = "index.md"
const val TRANSFORMATION_FILE = "transform.js"
const val SCHEMA_FILE = "schema.json"

@Singleton
class ProjectServiceImpl(
    private val fileSystem: FileSystemService,
    private val whiteListService: WhiteListFilterService
) : ProjectService {
    override fun detectProject(basePath: Path, whiteList: Path?): ProjectFolder {
        return loadProject(basePath)
            .let { filterProject(it, whiteList) }
            .also { it.log() }
    }

    fun loadProject(basePath: Path): ProjectFolder = ProjectFolder(
        basePath,
        fileExists(basePath, DESCRIPTION_FILE),
        fileSystem
            .listDirectories(basePath)
            .map { path ->
                NamespaceFolder(
                    path,
                    loadEvents(path),
                    fileExists(
                        path,
                        DESCRIPTION_FILE
                    )
                )
            }
    )

    @VisibleForTesting
    fun loadEvents(namespaceBasePath: Path) =
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

    @VisibleForTesting
    fun loadTransformations(eventBasePath: Path): List<TransformationFolder> =
        try {
            val transformationsFolder = eventBasePath.resolve(TRANSFORMATIONS_FOLDER)

            fileSystem.listDirectories(transformationsFolder)
                .map {
                    TransformationFolder(
                        it,
                        fileExists(
                            it,
                            TRANSFORMATION_FILE
                        )
                    )
                }
        } catch (e: NoSuchFileException) {
            emptyList()
        }

    @VisibleForTesting
    fun loadVersions(eventBasePath: Path) =
        try {
            val versionFolderPath = eventBasePath.resolve(VERSIONS_FOLDER)

            fileSystem
                .listDirectories(versionFolderPath)
                .map {
                    EventVersionFolder(
                        it,
                        fileExists(it, SCHEMA_FILE),
                        fileExists(
                            it,
                            DESCRIPTION_FILE
                        ),
                        loadExamples(it)
                    )
                }
        } catch (e: NoSuchFileException) {
            emptyList<EventVersionFolder>()
        }

    @VisibleForTesting
    fun loadExamples(eventBasePath: Path) =
        try {
            val examplePath = eventBasePath.resolve(EXAMPLES_FOLDER)

            fileSystem.listFiles(examplePath)
        } catch (e: NoSuchFileException) {
            emptyList<Path>()
        }

    @VisibleForTesting
    fun fileExists(folder: Path, fileName: String): Path? {
        val filePath = folder.resolve(fileName)

        return if (fileSystem.exists(filePath)) {
            filePath
        } else {
            null
        }
    }

    private fun filterProject(unfilteredProject: ProjectFolder, whiteList: Path?): ProjectFolder {
        return if (whiteList == null) {
            unfilteredProject
        } else {
            whiteListService.filter(unfilteredProject, fileSystem.readToStrings(whiteList.toFile()))
        }
    }
}
