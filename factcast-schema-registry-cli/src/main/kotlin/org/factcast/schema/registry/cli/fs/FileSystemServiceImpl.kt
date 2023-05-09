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

import com.github.fge.jackson.JsonLoader
import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URLConnection
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Singleton
import kotlin.streams.toList
import org.apache.commons.io.FileUtils
import org.factcast.schema.registry.cli.utils.filterJson

@Singleton
class FileSystemServiceImpl() : FileSystemService {
    override fun exists(path: Path) =
            Files.exists(path)

    override fun listDirectories(path: Path) =
            list(path)
                    .filter { x -> Files.isDirectory(x) }

    override fun listFiles(path: Path) =
            list(path)
                    .filter { x -> Files.isRegularFile(x) }

    override fun ensureDirectories(outputPath: Path) {
        Files.createDirectories(outputPath)
    }

    override fun writeToFile(filePath: File, body: String) {
        FileUtils.writeStringToFile(filePath, body, Charset.defaultCharset())
    }

    override fun readToString(filePath: File): String =
            FileUtils.readFileToString(filePath, Charset.defaultCharset())

    override fun readToStrings(filePath: File): List<String> =
        readToString(filePath).lines()

    override fun copyFile(from: File, to: File) {
        FileUtils.copyFile(from, to)
    }

    override fun readToJsonNode(path: Path) = try {
        JsonLoader.fromFile(path.toFile())
    } catch (e: IOException) {
        null
    }

    override fun deleteDirectory(path: Path) =
            FileUtils.deleteDirectory(path.toFile())

    override fun readToBytes(file: Path): ByteArray {
        return Files.readAllBytes(file)
    }

    override fun copyFromClasspath(source: String, target: Path) {
        val url = javaClass.classLoader.getResource(source)
                ?: throw IllegalArgumentException("didnt found '$source' on classpath")

        val urlConnection: URLConnection = url.openConnection()
        val toString = url.toString()
        if (toString.startsWith("file:")) {
            FileUtils.copyDirectory(File(url.path), target.toFile())
        } else if (toString.startsWith("jar:")) {
                copyJarResourcesRecursively(target.toFile(), urlConnection as JarURLConnection)
            } else throw IllegalStateException("not supported")
    }

    override fun copyFilteredJson(from: File, to: File, removedSchemaProps: Set<String>) {
        val jsonNode = this.readToJsonNode(from.toPath())
                ?: throw IllegalStateException("Loading JSON from $from failed")

        this.writeToFile(to, filterJson(jsonNode, removedSchemaProps).toString())
    }

    override fun copyDirectory(from: Path, to: Path) {
        FileUtils.copyDirectory(from.toFile(), to.toFile())
    }

    private fun list(path: Path) =
            Files.list(path).toList()

    private fun copyJarResourcesRecursively(destination: File, jarConnection: JarURLConnection) {
        val jarFile = jarConnection.jarFile

        for (entry in jarFile.entries()) {
            if (entry.name.startsWith(jarConnection.entryName)) {

                val fileName: String = entry.name.replace(jarConnection.entryName, "")
                val file = File(destination, fileName)

                if (!entry.isDirectory) {
                    jarFile.getInputStream(entry).use {
                        FileUtils.copyToFile(it, file)
                    }
                }
            }
        }
    }
}
