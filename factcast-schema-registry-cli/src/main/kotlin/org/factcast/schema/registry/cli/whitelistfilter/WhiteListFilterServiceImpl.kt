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
import java.nio.file.PathMatcher
import javax.inject.Singleton
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder

// returns a filtered ProjectFolder.
@Singleton
class WhiteListFilterServiceImpl : WhiteListFilterService {

    override fun filter(project: ProjectFolder, whiteList: List<String>): ProjectFolder {
        val whiteListMatchers = buildWhiteListPathMatchers(whiteList, project.path)

        return project.namespaces
                .map { filterNamespaceFolder(it, whiteListMatchers) }
                .filter { it.eventFolders.isNotEmpty() } // when there are no events, we exclude this namespace entirely
                .let { ProjectFolder(project.path, project.description, it) }
    }

    private fun filterNamespaceFolder(ns: NamespaceFolder, whiteListMatchers: List<PathMatcher>) =
            ns.eventFolders
                    .map { filterEventFolder(it, whiteListMatchers) }
                    .filter { it.versionFolders.isNotEmpty() } // when there are no versions, we exclude this event entirely
                    .let { NamespaceFolder(ns.path, it, ns.description) }

    private fun filterEventFolder(event: EventFolder, whiteListMatchers: List<PathMatcher>) =
            event.versionFolders
                    .filter { whiteListMatchers.any { matcher -> matcher.matches(it.path) } }
                    .let { EventFolder(event.path, it, event.description, event.transformationFolders) }

    private fun buildWhiteListPathMatchers(whiteList: List<String>, projectPath: Path) =
            whiteList
                    .map { "glob:${projectPath}$it" }
                    .map { FileSystems.getDefault().getPathMatcher(it) }
}
