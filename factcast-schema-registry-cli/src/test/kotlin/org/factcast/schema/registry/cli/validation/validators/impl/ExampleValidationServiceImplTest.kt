package org.factcast.schema.registry.cli.validation.validators.impl

import arrow.core.Left
import arrow.core.Right
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchema
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.matchers.collections.shouldHaveSingleElement
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Paths
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Example
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.utils.SchemaService
import org.factcast.schema.registry.cli.validation.ProjectError

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
            every { schemaService.loadSchema(dummyPath) } returns Left(error)

            uut.validateExamples(dummyProject) shouldHaveSingleElement error

            verify { schemaService.loadSchema(dummyPath) }
            confirmVerified(fs, schemaService)
        }

        "should fail on missing/corrupted example" {
            every { schemaService.loadSchema(dummyPath) } returns Right(mockSchema)
            every { fs.readToJsonNode(dummyPath) } returns null

            val result = uut.validateExamples(dummyProject)

            result shouldHaveSize 2
            result.all { it is ProjectError.NoSuchFile } shouldBe true

            verify { schemaService.loadSchema(dummyPath) }
            verify(exactly = 2) { fs.readToJsonNode(dummyPath) }
            confirmVerified(fs, schemaService)
        }

        "should fail on example validation error against schema" {
            every { schemaService.loadSchema(dummyPath) } returns Right(mockSchema)
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
            every { schemaService.loadSchema(dummyPath) } returns Right(mockSchema)
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
