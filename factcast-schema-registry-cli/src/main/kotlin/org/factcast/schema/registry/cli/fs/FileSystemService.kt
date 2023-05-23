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
package org.factcast.schema.registry.cli.fs

import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import java.nio.file.Path

interface FileSystemService {
    fun exists(path: Path): Boolean
    fun listDirectories(path: Path): List<Path>
    fun listFiles(path: Path): List<Path>
    fun ensureDirectories(outputPath: Path)
    fun writeToFile(filePath: File, body: String)
    fun readToString(filePath: File): String
    fun readToStrings(filePath: File): List<String>
    fun copyDirectory(from: Path, to: Path)
    fun copyFile(from: File, to: File)
    fun readToJsonNode(path: Path): JsonNode?
    fun deleteDirectory(path: Path)
    fun readToBytes(file: Path): ByteArray
    fun copyFromClasspath(source: String, target: Path)
    fun copyFilteredJson(from: File, to: File, removedSchemaProps: Set<String>)
}
