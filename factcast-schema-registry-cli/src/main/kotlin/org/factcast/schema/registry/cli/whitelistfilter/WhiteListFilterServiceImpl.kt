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
package org.factcast.schema.registry.cli.whitelistfilter

import jakarta.inject.Singleton
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.project.structure.TransformationFolder

// returns a filtered ProjectFolder.
@Singleton
class WhiteListFilterServiceImpl : WhiteListFilterService {

    override fun filter(project: ProjectFolder, whiteListEntries: List<String>): ProjectFolder {
        val whiteList = WhiteList(project.path, whiteListEntries)

        return project.namespaces
            .filter { it.containedIn(whiteList) }
            .map { filterNamespaceFolder(it, whiteList) }
            .let { ProjectFolder(project.path, project.description, it) }
    }

    fun filterNamespaceFolder(ns: NamespaceFolder, whiteList: WhiteList) =
        ns.eventFolders
            .filter { it.containedIn(whiteList) }
            .map { filterEventFolder(it, whiteList) }
            .let { NamespaceFolder(ns.path, it, ns.description) }

    private fun filterEventFolder(event: EventFolder, whiteList: WhiteList): EventFolder {
        val filteredVersionsFolder = event.versionFolders
            .filter { it.containedIn(whiteList) }

        val filteredTransformationFolders = event.transformationFolders
            .filter {
                try {
                    val (fromVersion, toVersion) = determineTransformationVersions(it)
                    eventVersionsContainsVersion(filteredVersionsFolder, fromVersion) &&
                            eventVersionsContainsVersion(filteredVersionsFolder, toVersion)
                } catch (e: IndexOutOfBoundsException) {
                    true // on parse error, ignore transformation from white listing
                }
            }

        return EventFolder(event.path, filteredVersionsFolder, event.description, filteredTransformationFolders)
    }

    private fun determineTransformationVersions(it: TransformationFolder) =
        it.path.fileName.toString().split("-")

    private fun eventVersionsContainsVersion(eventVersions: List<EventVersionFolder>, version: String): Boolean =
        eventVersions.any { it.path.fileName.toString() == version }
}
