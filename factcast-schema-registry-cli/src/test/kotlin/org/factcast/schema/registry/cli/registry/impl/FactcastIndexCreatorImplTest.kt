package org.factcast.schema.registry.cli.registry.impl

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.mockk.*
import org.factcast.schema.registry.cli.domain.*
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

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
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
                    match { it.path.platformIndependent().endsWith(getTransformationId(namespace1, event1, 1, 2)) })
            }
            verify {
                fs.copyFile(
                    any(),
                    match { it.path.platformIndependent().endsWith(getTransformationId(namespace1, event1, 2, 1)) })
            }
            confirmVerified(fs)
        }
    }
}

fun String.platformIndependent() = this.replace(File.separator, "/")
