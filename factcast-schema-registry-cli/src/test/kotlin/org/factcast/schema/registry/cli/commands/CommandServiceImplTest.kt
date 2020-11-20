package org.factcast.schema.registry.cli.commands

import arrow.core.Left
import arrow.core.Right
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.ProjectService
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.registry.DistributionCreatorService
import org.factcast.schema.registry.cli.validation.ValidationService

class CommandServiceImplTest : StringSpec() {
    val fs = mockk<FileSystemService>()
    val validationService = mockk<ValidationService>()
    val projectService = mockk<ProjectService>()
    val distributionCreatorService = mockk<DistributionCreatorService>()
    val dummyPath = Paths.get(".")
    val dummyProjectFolder = ProjectFolder(dummyPath, dummyPath, emptyList())
    val dummyProject = Project(dummyPath, emptyList())

    val uut = CommandServiceImpl(fs, validationService, projectService, distributionCreatorService)

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "build a proper project" {
            every { fs.deleteDirectory(dummyPath) } returns Unit
            every { projectService.detectProject(dummyPath, null) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Right(dummyProject)
            every { distributionCreatorService.createDistributable(dummyPath, dummyProject) } returns Unit

            uut.build(dummyPath, dummyPath) shouldBe 0

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath, null)
                validationService.validateProject(dummyProjectFolder)
                distributionCreatorService.createDistributable(dummyPath, dummyProject)
            }
        }

        "build a broken project" {
            every { fs.deleteDirectory(dummyPath) } returns Unit
            every { projectService.detectProject(dummyPath, null) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Left(emptyList())

            uut.build(dummyPath, dummyPath) shouldBe 1

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath, null)
                validationService.validateProject(dummyProjectFolder)
            }
        }

        "build a proper project but fail on creation" {
            every { fs.deleteDirectory(dummyPath) } returns Unit
            every { projectService.detectProject(dummyPath, null) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Right(dummyProject)
            every { distributionCreatorService.createDistributable(dummyPath, dummyProject) } throws IOException("")

            uut.build(dummyPath, dummyPath) shouldBe 1

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath, null)
                validationService.validateProject(dummyProjectFolder)
                distributionCreatorService.createDistributable(dummyPath, dummyProject)
            }
        }

        "build a proper project but fail on wrong paths" {
            every { fs.deleteDirectory(dummyPath) } returns Unit
            every { projectService.detectProject(dummyPath, null) } throws InvalidPathException("", "")

            uut.build(dummyPath, dummyPath) shouldBe 1

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath, null)
            }
        }

        "validate a proper project" {
            every { projectService.detectProject(dummyPath, null) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Right(dummyProject)

            uut.validate(dummyPath, null) shouldBe 0

            verifyAll {
                projectService.detectProject(dummyPath, null)
                validationService.validateProject(dummyProjectFolder)
            }
        }

        "validate broken project" {
            every { projectService.detectProject(dummyPath, null) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Left(emptyList())

            uut.validate(dummyPath, null) shouldBe 1

            verifyAll {
                projectService.detectProject(dummyPath, null)
                validationService.validateProject(dummyProjectFolder)
            }
        }

        "validate on wrong input path" {
            every { projectService.detectProject(dummyPath, null) } throws InvalidPathException("", "")

            uut.validate(dummyPath, null) shouldBe 1

            verifyAll { projectService.detectProject(dummyPath, null) }
        }
    }
}
