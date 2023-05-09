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

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

class WhiteList(val projectPath: Path, whiteList: List<String>) {

    private val whiteListMatchers = buildMatchers(whiteList)

    fun matches(path: Path): Boolean =
        whiteListMatchers.any { it.matches(path) }

    private fun buildMatchers(whiteList: List<String>): List<PathMatcher> =
        whiteList
            .map(this::buildGlobPattern)
            .map { FileSystems.getDefault().getPathMatcher(it) }

    private fun buildGlobPattern(whiteListEntry: String) =
        "glob:" + projectPath.toString().replace(File.separator, "/") + whiteListEntry
}
