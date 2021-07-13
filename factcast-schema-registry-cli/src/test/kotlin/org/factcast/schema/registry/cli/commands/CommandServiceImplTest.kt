package org.factcast.schema.registry.cli.commands

import arrow.core.Either
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.ProjectService
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.registry.DistributionCreatorService
import org.factcast.schema.registry.cli.validation.ValidationService
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Paths

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
            every { projectService.detectProject(dummyPath) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Right(dummyProject)
            every { distributionCreatorService.createDistributable(dummyPath, dummyProject) } returns Unit

            uut.build(dummyPath, dummyPath) shouldBe 0

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath)
                validationService.validateProject(dummyProjectFolder)
                distributionCreatorService.createDistributable(dummyPath, dummyProject)
            }
        }

        "build a broken project" {
            every { fs.deleteDirectory(dummyPath) } returns Unit
            every { projectService.detectProject(dummyPath) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Left(emptyList())

            uut.build(dummyPath, dummyPath) shouldBe 1

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath)
                validationService.validateProject(dummyProjectFolder)
            }
        }

        "build a proper project but fail on creation" {
            every { fs.deleteDirectory(dummyPath) } returns Unit
            every { projectService.detectProject(dummyPath) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Right(dummyProject)
            every { distributionCreatorService.createDistributable(dummyPath, dummyProject) } throws IOException("")

            uut.build(dummyPath, dummyPath) shouldBe 1

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath)
                validationService.validateProject(dummyProjectFolder)
                distributionCreatorService.createDistributable(dummyPath, dummyProject)
            }
        }

        "build a proper project but fail on wrong paths" {
            every { fs.deleteDirectory(dummyPath) } returns Unit
            every { projectService.detectProject(dummyPath) } throws InvalidPathException("", "")

            uut.build(dummyPath, dummyPath) shouldBe 1

            verifyAll {
                fs.deleteDirectory(dummyPath)
                projectService.detectProject(dummyPath)
            }
        }

        "validate a proper project" {
            every { projectService.detectProject(dummyPath) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Right(dummyProject)

            uut.validate(dummyPath) shouldBe 0

            verifyAll {
                projectService.detectProject(dummyPath)
                validationService.validateProject(dummyProjectFolder)
            }
        }

        "validate broken project" {
            every { projectService.detectProject(dummyPath) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Left(emptyList())

            uut.validate(dummyPath) shouldBe 1

            verifyAll {
                projectService.detectProject(dummyPath)
                validationService.validateProject(dummyProjectFolder)
            }
        }

        "validate on wrong input path" {
            every { projectService.detectProject(dummyPath) } throws InvalidPathException("", "")

            uut.validate(dummyPath) shouldBe 1

            verifyAll { projectService.detectProject(dummyPath) }
        }
    }
}
