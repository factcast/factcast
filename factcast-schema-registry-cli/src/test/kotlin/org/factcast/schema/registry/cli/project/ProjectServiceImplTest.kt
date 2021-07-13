package org.factcast.schema.registry.cli.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.*
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.impl.EXAMPLES_FOLDER
import org.factcast.schema.registry.cli.project.impl.ProjectServiceImpl
import org.factcast.schema.registry.cli.project.impl.TRANSFORMATIONS_FOLDER
import org.factcast.schema.registry.cli.project.impl.VERSIONS_FOLDER
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.whitelistfilter.WhiteListFilterService
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

class ProjectServiceImplTest : StringSpec() {
    val fs = mockk<FileSystemService>()
    val whiteListService = mockk<WhiteListFilterService>()
    val dummyPath = Paths.get(".")
    val examplesPath = dummyPath.resolve(EXAMPLES_FOLDER)
    val versionsPath = dummyPath.resolve(VERSIONS_FOLDER)
    val transfromationsPath = dummyPath.resolve(TRANSFORMATIONS_FOLDER)

    val uut = ProjectServiceImpl(fs, whiteListService)

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "fileExists - should return path if present" {
            every { fs.exists(any()) } returns true

            val result = uut.fileExists(dummyPath, "foo")
            result shouldBe dummyPath.resolve("foo")

            verify { fs.exists(any()) }
            confirmVerified(fs)
        }

        "fileExists - should return null if absent" {
            every { fs.exists(any()) } returns false

            val result = uut.fileExists(dummyPath, "foo")
            result shouldBe null

            verify { fs.exists(any()) }
            confirmVerified(fs)
        }

        "loadExamples - should return a list of paths" {
            every { fs.listFiles(examplesPath) } returns listOf(dummyPath)

            val result = uut.loadExamples(dummyPath)
            result shouldHaveSize 1

            verify { fs.listFiles(examplesPath) }
            confirmVerified(fs)
        }

        "loadExamples - should return an empty list" {
            every { fs.listFiles(examplesPath) } throws NoSuchFileException("foo")

            val result = uut.loadExamples(dummyPath)
            result shouldHaveSize 0

            verify { fs.listFiles(examplesPath) }
            confirmVerified(fs)
        }

        "loadVersions - should return a list of versions" {
            every { fs.exists(any()) } returns true
            every { fs.listFiles(examplesPath) } returns listOf(dummyPath)
            every { fs.listDirectories(versionsPath) } returns listOf(dummyPath)

            val result = uut.loadVersions(dummyPath)
            result shouldHaveSize 1

            verifyAll {
                fs.listFiles(examplesPath)
                fs.listDirectories(versionsPath)
                fs.exists(any())
            }
            confirmVerified(fs)
        }

        "loadVersions - should return an empty list of versions" {
            every { fs.exists(any()) } returns true
            every { fs.listDirectories(versionsPath) } throws NoSuchFileException("foo")

            val result = uut.loadVersions(dummyPath)
            result shouldHaveSize 0

            verify { fs.listDirectories(versionsPath) }
            confirmVerified(fs)
        }

        "loadTransformations - should return a list of transformations" {
            every { fs.exists(any()) } returns true
            every { fs.listDirectories(transfromationsPath) } returns listOf(dummyPath)

            val result = uut.loadTransformations(dummyPath)
            result shouldHaveSize 1

            verifyAll {
                fs.listDirectories(transfromationsPath)
                fs.exists(any())
            }
            confirmVerified(fs)
        }

        "loadTransformations - should return an empty list of transformations" {
            every { fs.listDirectories(transfromationsPath) } throws NoSuchFileException("foo")

            val result = uut.loadTransformations(dummyPath)
            result shouldHaveSize 0

            verify { fs.listDirectories(transfromationsPath) }
            confirmVerified(fs)
        }

        "loadEvents - should return a list of events" {
            every { fs.listDirectories(dummyPath) } returns listOf(dummyPath)
            every { fs.listDirectories(transfromationsPath) } returns emptyList()
            every { fs.listDirectories(versionsPath) } returns emptyList()
            every { fs.exists(any()) } returns true

            val result = uut.loadEvents(dummyPath)
            result shouldHaveSize 1

            verifyAll {
                fs.listDirectories(dummyPath)
                fs.listDirectories(transfromationsPath)
                fs.listDirectories(versionsPath)
                fs.exists(any())
            }
            confirmVerified(fs)
        }

        "loadEvents - should return an empty list of events" {
            every { fs.listDirectories(dummyPath) } throws NoSuchFileException("foo")

            val result = uut.loadEvents(dummyPath)
            result shouldHaveSize 0

            verify { fs.listDirectories(dummyPath) }
            confirmVerified(fs)
        }

        "detectProject - should return project (w/o namespaces)" {
            every { fs.exists(any()) } returns true
            every { fs.listDirectories(dummyPath) } returns emptyList()

            val result = uut.detectProject(dummyPath)
            result.shouldBeInstanceOf<ProjectFolder>()
            result.namespaces shouldHaveSize 0

            verifyAll {
                fs.exists(any())
                fs.listDirectories(dummyPath)
            }
            confirmVerified(fs)
        }

        "detectProject - should return project (w/ namespaces)" {
            every { fs.listDirectories(dummyPath) } returns listOf(dummyPath)
            every { fs.listDirectories(transfromationsPath) } returns emptyList()
            every { fs.listDirectories(versionsPath) } returns emptyList()
            every { fs.exists(any()) } returns true

            val result = uut.detectProject(dummyPath)
            result.shouldBeInstanceOf<ProjectFolder>()
            result.namespaces shouldHaveSize 1

            verifyAll {
                fs.listDirectories(dummyPath)
                fs.listDirectories(transfromationsPath)
                fs.listDirectories(versionsPath)
                fs.exists(any())
            }
            confirmVerified(fs)
        }

        "detectProject - filter is invoked when whitelist is supplied" {
            val filteredProjectFolder = mockk<ProjectFolder>()

            every { fs.exists(any()) } returns true
            every { fs.listDirectories(dummyPath) } returns emptyList()
            every { fs.readToStrings(dummyPath.toFile()) } returns emptyList()
            every { whiteListService.filter(any(), any()) } returns filteredProjectFolder
            every { filteredProjectFolder.namespaces } returns emptyList()

            val result = uut.detectProject(dummyPath, dummyPath)
            result shouldBeSameInstanceAs filteredProjectFolder

            verifyAll {
                fs.exists(any())
                fs.listDirectories(dummyPath)
                fs.readToStrings(dummyPath.toFile())
                whiteListService.filter(any(), any())
            }
            confirmVerified(fs)
            confirmVerified(whiteListService)
        }
    }
}
