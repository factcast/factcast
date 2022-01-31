package org.factcast.schema.registry.cli.commands

import arrow.core.Either
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.paths.*
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.apache.commons.io.FileUtils
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.ProjectService
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.registry.DistributionCreatorService
import org.factcast.schema.registry.cli.validation.ValidationService
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText

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
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Right(
                dummyProject
            )
            every {
                distributionCreatorService.createDistributable(
                    dummyPath,
                    dummyProject
                )
            } returns Unit

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
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Left(
                emptyList()
            )

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
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Right(
                dummyProject
            )
            every {
                distributionCreatorService.createDistributable(
                    dummyPath,
                    dummyProject
                )
            } throws IOException("")

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
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Right(
                dummyProject
            )

            uut.validate(dummyPath) shouldBe 0

            verifyAll {
                projectService.detectProject(dummyPath)
                validationService.validateProject(dummyProjectFolder)
            }
        }

        "validate broken project" {
            every { projectService.detectProject(dummyPath) } returns dummyProjectFolder
            every { validationService.validateProject(dummyProjectFolder) } returns Either.Left(
                emptyList()
            )

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

        "Create empty schema registry" {
            val p = createTempDirectory()

            try {
                uut.create(p)

                val rootDir = p.resolve("factcast-example-schema-registry")

                rootDir.shouldBeADirectory()
                rootDir.shouldNotBeEmptyDirectory()

                rootDir.resolve("pom.xml").shouldExist()

                val resourcesDir = rootDir.resolve("src/main/resources")
                resourcesDir.shouldBeADirectory()
                resourcesDir.shouldContainFile("index.md")

                val users = resourcesDir.resolve("users")
                users.shouldBeADirectory()
                users.shouldContainFile("index.md")

                val userCreated = users.resolve("UserCreated")

                val transformationDir = userCreated.resolve("transformations")
                transformationDir.shouldBeADirectory()
                transformationDir.shouldContainFiles("1-2", "2-3", "3-2")

                checkTransformation(transformationDir, "1-2")
                checkTransformation(transformationDir, "2-3")
                checkTransformation(transformationDir, "3-2")

                transformationDir.resolve("1-2/transform.js")
                    .readText() shouldBe """
                        function transform(event) {
                            event.salutation = "NA"
                        }
                        
                    """.trimIndent()


                transformationDir.resolve("2-3/transform.js")
                    .readText() shouldBe """
                        function transform(event) {
                            event.displayName = event.firstName + " " + event.lastName
                        }
                    """.trimIndent()


                transformationDir.resolve("3-2/transform.js")
                    .readText() shouldBe """
                        function transform(event) {
                            // just a dummy downcast transformation
                        }
                    """.trimIndent()

                val versionDir = userCreated.resolve("versions")

                checkVersion(versionDir, "1")
                checkVersion(versionDir, "2")
                checkVersion(versionDir, "3")

                versionDir.resolve("1/schema.json").readText() shouldBe """
                    {
                      "additionalProperties" : true,
                      "properties" : {
                        "firstName" : {
                          "type": "string"
                        },
                        "lastName" : {
                          "type": "string"
                        }
                      },
                      "required": ["firstName", "lastName"]
                    }
                """.trimIndent()

                versionDir.resolve("2/schema.json").readText() shouldBe """
                    {
                      "additionalProperties" : true,
                      "properties" : {
                        "firstName" : {
                          "type": "string"
                        },
                        "lastName" : {
                          "type": "string"
                        },
                        "salutation": {
                          "type": "string",
                          "enum": ["Mr", "Mrs", "NA"]
                        }
                      },
                      "required": ["firstName", "lastName", "salutation"]
                    }
                """.trimIndent()

                versionDir.resolve("3/schema.json").readText() shouldBe """
                    {
                      "additionalProperties" : true,
                      "properties" : {
                        "firstName" : {
                          "type": "string"
                        },
                        "lastName" : {
                          "type": "string"
                        },
                        "salutation": {
                          "type": "string",
                          "enum": ["Mr", "Mrs", "NA"]
                        },
                        "displayName": {
                          "type": "string"
                        }
                      },
                      "required": ["firstName", "lastName", "salutation", "displayName"]
                    }
                """.trimIndent()

            } finally {
                FileUtils.deleteQuietly(p.toFile())
            }
        }
    }

    private fun checkTransformation(transformationDir: Path, path: String) {
        val dir = transformationDir.resolve(path)
        dir.shouldBeADirectory()
        dir.shouldNotBeEmptyDirectory()
        dir.shouldContainFile("transform.js")
    }

    private fun checkVersion(versionDir: Path, version: String) {
        val dir = versionDir.resolve(version)
        dir.shouldBeADirectory()
        dir.shouldContainFiles("index.md", "schema.json")
        dir.resolve("examples").shouldContainFile("simple.json")
    }
}
