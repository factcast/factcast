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
package org.factcast.schema.registry.cli.validation.validators.impl

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchema
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Example
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.utils.SchemaService
import org.factcast.schema.registry.cli.validation.ProjectError
import java.nio.file.Paths

class ExampleValidationServiceImplTest : StringSpec() {
    val fs = mockk<FileSystemService>()
    val schemaService = mockk<SchemaService>()
    val mockSchema = mockk<JsonSchema>()
    val mockJsonNode = mockk<JsonNode>()
    val mockProcessingReport = mockk<ProcessingReport>()

    val dummyPath = Paths.get(".")

    val example1 = Example("ex1", dummyPath)
    val example2 = Example("ex2", dummyPath)
    val version1 = Version(1, dummyPath, dummyPath, listOf(example1, example2))
    val event1 = Event("bar", dummyPath, listOf(version1), emptyList())
    val namespace1 = Namespace("foo", dummyPath, listOf(event1))
    val dummyProject = Project(null, listOf(namespace1))

    val uut = ExampleValidationServiceImpl(fs, schemaService)

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "should fail on schema error" {
            val error = ProjectError.NoSchema(dummyPath)
            every { schemaService.loadSchema(dummyPath) } returns Either.Left(error)

            uut.validateExamples(dummyProject) shouldHaveSingleElement error

            verify { schemaService.loadSchema(dummyPath) }
            confirmVerified(fs, schemaService)
        }

        "should fail on missing/corrupted example" {
            every { schemaService.loadSchema(dummyPath) } returns Either.Right(mockSchema)
            every { fs.readToJsonNode(dummyPath) } returns null

            val result = uut.validateExamples(dummyProject)

            result shouldHaveSize 2
            result.all { it is ProjectError.NoSuchFile } shouldBe true

            verify { schemaService.loadSchema(dummyPath) }
            verify(exactly = 2) { fs.readToJsonNode(dummyPath) }
            confirmVerified(fs, schemaService)
        }

        "should fail on example validation error against schema" {
            every { schemaService.loadSchema(dummyPath) } returns Either.Right(mockSchema)
            every { fs.readToJsonNode(dummyPath) } returns mockJsonNode
            every { mockSchema.validate(mockJsonNode) } returns mockProcessingReport
            every { mockProcessingReport.isSuccess } returns false

            val result = uut.validateExamples(dummyProject)

            result shouldHaveSize 2
            result.all { it is ProjectError.ValidationError } shouldBe true

            verify { schemaService.loadSchema(dummyPath) }
            verify(exactly = 2) { fs.readToJsonNode(dummyPath) }
            verify(exactly = 2) { mockSchema.validate(mockJsonNode) }
            confirmVerified(fs, schemaService, mockSchema)
        }

        "should succeed example validation" {
            every { schemaService.loadSchema(dummyPath) } returns Either.Right(mockSchema)
            every { fs.readToJsonNode(dummyPath) } returns mockJsonNode
            every { mockSchema.validate(mockJsonNode) } returns mockProcessingReport
            every { mockProcessingReport.isSuccess } returns true

            val result = uut.validateExamples(dummyProject)

            result shouldHaveSize 0

            verify { schemaService.loadSchema(dummyPath) }
            verify(exactly = 2) { fs.readToJsonNode(dummyPath) }
            verify(exactly = 2) { mockSchema.validate(mockJsonNode) }
            confirmVerified(fs, schemaService, mockSchema)
        }
    }
}
