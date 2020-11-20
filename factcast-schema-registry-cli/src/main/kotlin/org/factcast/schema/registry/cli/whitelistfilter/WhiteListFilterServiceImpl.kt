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

import java.nio.file.FileSystems
import java.nio.file.Path
import javax.inject.Singleton
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder

// returns a filtered ProjectFolder.
// the original structure is iterated, filtered and freshly rebuild from inside to outside
@Singleton
class WhiteListFilterServiceImpl : WhiteListFilterService {

    override fun filter(project: ProjectFolder, whiteList: List<String>): ProjectFolder {
        val whiteListPathMatchers = buildWhiteListPathMatchers(whiteList, project.path)

        var namespaces = mutableListOf<NamespaceFolder>()
        project.namespaces.forEach { nameSpaceFolder ->

            var eventFolders = mutableListOf<EventFolder>()
            nameSpaceFolder.eventFolders.forEach { eventFolder ->

                var eventVersionFolders = mutableListOf<EventVersionFolder>()
                eventFolder.versionFolders.forEach { eventVersionFolder ->

                    if (whiteListPathMatchers.any { pathMatcher -> pathMatcher.matches(eventVersionFolder.path) }) {
                        eventVersionFolders.add(eventVersionFolder)
                    }
                }

                if (eventVersionFolders.isEmpty()) return@forEach // if there is no version ignore the event completely
                eventFolders.add(EventFolder(
                        eventFolder.path, eventVersionFolders, eventFolder.description, eventFolder.transformationFolders))
            }
            namespaces.add(NamespaceFolder(nameSpaceFolder.path, eventFolders, nameSpaceFolder.description))
        }

        return ProjectFolder(project.path, project.description, namespaces)
    }

    private fun buildWhiteListPathMatchers(whiteList: List<String>, projectPath: Path) =
            whiteList
                    .map { "glob:${projectPath}$it" }
                    .map { FileSystems.getDefault().getPathMatcher(it) }
}
