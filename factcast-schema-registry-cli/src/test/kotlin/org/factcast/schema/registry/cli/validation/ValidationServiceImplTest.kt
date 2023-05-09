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
package org.factcast.schema.registry.cli.validation

import arrow.core.Either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.validation.validators.ExampleValidationService
import org.factcast.schema.registry.cli.validation.validators.ProjectStructureValidationService
import org.factcast.schema.registry.cli.validation.validators.TransformationValidationService
import java.nio.file.Paths

class ValidationServiceImplTest : StringSpec() {
    val validateExamplesServiceMock = mockk<ExampleValidationService>()
    val validateTransformationsServiceMock = mockk<TransformationValidationService>()
    val validateProjectStructureServiceMock = mockk<ProjectStructureValidationService>()
    val dummyPath = Paths.get(".")
    val dummyProjectFolder = ProjectFolder(dummyPath, null, emptyList())
    val dummyProject = Project(dummyPath, emptyList())

    val uut = ValidationServiceImpl(
        validateExamplesServiceMock,
        validateTransformationsServiceMock,
        validateProjectStructureServiceMock
    )

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "fail fast on wrong project structure" {
            val errorList = listOf(ProjectError.NoSuchFile(dummyPath))
            every { validateProjectStructureServiceMock.validateProjectStructure(dummyProjectFolder) } returns Either.Left(
                errorList
            )

            uut.validateProject(dummyProjectFolder).shouldBeLeft().also {
                it shouldBe errorList
            }

            verifyAll { validateProjectStructureServiceMock.validateProjectStructure(dummyProjectFolder) }
        }

        "fail on broken examples and transformations" {
            val errorList = listOf(ProjectError.NoSuchFile(dummyPath))
            every { validateProjectStructureServiceMock.validateProjectStructure(dummyProjectFolder) } returns Either.Right(
                dummyProject
            )
            every { validateExamplesServiceMock.validateExamples(dummyProject) } returns errorList
            every { validateTransformationsServiceMock.validateTransformations(dummyProject) } returns errorList

            uut.validateProject(dummyProjectFolder).shouldBeLeft().also {
                it shouldHaveSize 2
                it[0].shouldBeInstanceOf<ProjectError.NoSuchFile>()
                it[1].shouldBeInstanceOf<ProjectError.NoSuchFile>()
            }

            verifyAll {
                validateProjectStructureServiceMock.validateProjectStructure(dummyProjectFolder)
                validateTransformationsServiceMock.validateTransformations(dummyProject)
                validateExamplesServiceMock.validateExamples(dummyProject)
            }
        }

        "succeed for a valid project" {
            every { validateProjectStructureServiceMock.validateProjectStructure(dummyProjectFolder) } returns Either.Right(
                dummyProject
            )
            every { validateExamplesServiceMock.validateExamples(dummyProject) } returns emptyList()
            every { validateTransformationsServiceMock.validateTransformations(dummyProject) } returns emptyList()

            uut.validateProject(dummyProjectFolder).shouldBeRight().also {
                it shouldBe dummyProject
            }

            verifyAll {
                validateProjectStructureServiceMock.validateProjectStructure(dummyProjectFolder)
                validateTransformationsServiceMock.validateTransformations(dummyProject)
                validateExamplesServiceMock.validateExamples(dummyProject)
            }
        }
    }
}
