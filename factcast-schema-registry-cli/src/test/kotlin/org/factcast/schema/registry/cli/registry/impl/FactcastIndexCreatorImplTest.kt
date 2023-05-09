/*
 * Copyright © 2017-2023 factcast.org
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

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Example
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.registry.IndexFileCalculator
import org.factcast.schema.registry.cli.registry.getEventId
import org.factcast.schema.registry.cli.registry.getTransformationId
import org.factcast.schema.registry.cli.registry.index.Index
import java.io.File
import java.nio.file.Paths

class FactcastIndexCreatorImplTest : StringSpec() {
    val indexFileCalculator = mockk<IndexFileCalculator>()
    val fs = mockk<FileSystemService>()
    val om = mockk<ObjectMapper>()
    val dummyPath = Paths.get(".")
    val example1 = Example("ex1", dummyPath)
    val example2 = Example("ex2", dummyPath)
    val transformation1to2 = Transformation(1, 2, dummyPath)
    val transformation2to1 = Transformation(2, 1, dummyPath)
    val version1 = Version(1, dummyPath, dummyPath, listOf(example1, example2))
    val version2 = Version(2, dummyPath, dummyPath, listOf(example1, example2))
    val event1 = Event("bar", dummyPath, listOf(version1, version2), listOf(transformation1to2, transformation2to1))
    val namespace1 = Namespace("foo", dummyPath, listOf(event1))
    val dummyProject = Project(null, listOf(namespace1))

    val uut = FactcastIndexCreatorImpl(fs, om, indexFileCalculator)

    val titleFiltered = setOf("title")

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "createIndexFile - should create a valid index file" {
            val index = Index(emptyList(), emptyList())
            every { indexFileCalculator.calculateIndex(dummyProject, titleFiltered) } returns index
            every { fs.ensureDirectories(dummyPath) } returns Unit
            every { om.writeValue(any<File>(), index) } returns Unit

            uut.createIndexFile(dummyPath, dummyProject, titleFiltered)

            verifyAll {
                indexFileCalculator.calculateIndex(dummyProject, titleFiltered)
                fs.ensureDirectories(dummyPath)
                om.writeValue(match<File> { it.path.platformIndependent().endsWith("index.json") }, index)
            }
            confirmVerified(indexFileCalculator, fs, om)
        }

        "copySchemes - should copy the schema for each version with stripped title attribute" {
            every { fs.copyFilteredJson(dummyPath.toFile(), any(), any()) } returns Unit

            uut.copySchemes(dummyPath, dummyProject, titleFiltered)

            verifyAll {
                fs.copyFilteredJson(
                    any(),
                    match { it.path.platformIndependent().endsWith(getEventId(namespace1, event1, version1)) },
                    titleFiltered
                )
                fs.copyFilteredJson(
                    any(),
                    match { it.path.platformIndependent().endsWith(getEventId(namespace1, event1, version2)) },
                    titleFiltered
                )
            }
            confirmVerified(fs)
        }

        "copyTransformations - should copy the transformations for each version" {
            every { fs.copyFile(dummyPath.toFile(), any()) } returns Unit

            uut.copyTransformations(dummyPath, dummyProject)

            verify {
                fs.copyFile(
                    any(),
                    match { it.path.platformIndependent().endsWith(getTransformationId(namespace1, event1, 1, 2)) }
                )
            }
            verify {
                fs.copyFile(
                    any(),
                    match { it.path.platformIndependent().endsWith(getTransformationId(namespace1, event1, 2, 1)) }
                )
            }
            confirmVerified(fs)
        }
    }
}

fun String.platformIndependent() = this.replace(File.separator, "/")
