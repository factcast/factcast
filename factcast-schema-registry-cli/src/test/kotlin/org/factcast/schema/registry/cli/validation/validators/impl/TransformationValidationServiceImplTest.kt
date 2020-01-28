package org.factcast.schema.registry.cli.validation.validators.impl

import arrow.core.Left
import arrow.core.Right
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchema
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Example
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.utils.SchemaService
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import org.factcast.schema.registry.cli.validation.ProjectError
import org.factcast.schema.registry.cli.validation.TransformationEvaluator
import java.nio.file.Paths

class TransformationValidationServiceImplTest : StringSpec() {
    val missingTransformationCalculator = mockk<MissingTransformationCalculator>()
    val fs = mockk<FileSystemService>()
    val schemaService = mockk<SchemaService>()
    val transformationEvaluator = mockk<TransformationEvaluator>()
    val jsonNodeMock = mockk<JsonNode>()
    val schemaMock = mockk<JsonSchema>()
    val processingResultMock = mockk<ProcessingReport>()

    val dummyPath = Paths.get(".")

    val transformation1to2 = Transformation(1, 2, dummyPath)
    val transformation2to3 = Transformation(2, 3, dummyPath)
    val transformation3to4 = Transformation(3, 4, dummyPath)
    val version1 = Version(1, dummyPath, dummyPath, listOf(Example("foo", dummyPath)))
    val version2 = Version(2, dummyPath, dummyPath, listOf(Example("bar", dummyPath)))
    val event1 = Event("bar", dummyPath, listOf(version1, version2), listOf(transformation1to2))
    val event2 = Event(
        "bar",
        dummyPath,
        listOf(version1, version2),
        listOf(transformation2to3, transformation3to4)
    )

    val uut = TransformationValidationServiceImpl(
        missingTransformationCalculator,
        fs,
        schemaService,
        transformationEvaluator
    )

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "calculateMissingUpcastTransformations - should return no errors for valid transformations" {
            val namespace = Namespace("foo", dummyPath, listOf(event1, event1))
            val dummyProject = Project(null, listOf(namespace))
            every { missingTransformationCalculator.calculateUpcastTransformations(event1) } returns emptyList()

            uut.calculateMissingUpcastTransformations(dummyProject) shouldHaveSize 0

            verify(exactly = 2) { missingTransformationCalculator.calculateUpcastTransformations(event1) }
            confirmVerified(missingTransformationCalculator)
        }

        "calculateMissingUpcastTransformations - should return no errors for valid transformations " {
            val namespace = Namespace("foo", dummyPath, listOf(event1, event1))
            val dummyProject = Project(null, listOf(namespace))

            every { missingTransformationCalculator.calculateUpcastTransformations(event1) } returns listOf(
                Pair(
                    version1,
                    version2
                )
            )

            val result = uut.calculateMissingUpcastTransformations(dummyProject)
            result shouldHaveSize 2
            result.all { it is ProjectError.NoUpcastForVersion } shouldBe true

            verify(exactly = 2) { missingTransformationCalculator.calculateUpcastTransformations(event1) }
            confirmVerified(missingTransformationCalculator)
        }

        "calculateValidationErrors - should fail for transformations for non existent version" {
            val namespace = Namespace("foo", dummyPath, listOf(event2))
            val dummyProject = Project(null, listOf(namespace))

            val result = uut.calculateValidationErrors(dummyProject)

            result shouldHaveSize 2
            result.any { it is ProjectError.MissingVersionForTransformation } shouldBe true
        }

        "calculateValidationErrors - should fail for non existing schema" {
            val namespace = Namespace("foo", dummyPath, listOf(event1))
            val dummyProject = Project(null, listOf(namespace))

            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { schemaService.loadSchema(dummyPath) } returns Left(ProjectError.NoSchema(dummyPath))

            val result = uut.calculateValidationErrors(dummyProject)

            result shouldHaveSize 1
            result[0].shouldBeInstanceOf<ProjectError.NoSchema>()

            verify {
                schemaService.loadSchema(dummyPath)
            }
            confirmVerified(schemaService)
        }

        "calculateValidationErrors - should fail for errors in schema validation" {
            val namespace = Namespace("foo", dummyPath, listOf(event1))
            val dummyProject = Project(null, listOf(namespace))

            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { schemaService.loadSchema(dummyPath) } returns Right(schemaMock)
            every { transformationEvaluator.evaluate(dummyPath, jsonNodeMock) } returns jsonNodeMock
            every { schemaMock.validate(jsonNodeMock) } returns processingResultMock
            every { processingResultMock.isSuccess } returns false

            val result = uut.calculateValidationErrors(dummyProject)

            result shouldHaveSize 1
            result[0].shouldBeInstanceOf<ProjectError.TransformationValidationError>()

            verify { transformationEvaluator.evaluate(dummyPath, jsonNodeMock) }
            verify { schemaMock.validate(jsonNodeMock) }
            verify { schemaService.loadSchema(dummyPath) }
            confirmVerified(transformationEvaluator, schemaMock)
        }

        "calculateValidationErrors - should succeed" {
            val namespace = Namespace("foo", dummyPath, listOf(event1))
            val dummyProject = Project(null, listOf(namespace))

            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { schemaService.loadSchema(dummyPath) } returns Right(schemaMock)
            every { transformationEvaluator.evaluate(dummyPath, jsonNodeMock) } returns jsonNodeMock
            every { schemaMock.validate(jsonNodeMock) } returns processingResultMock
            every { processingResultMock.isSuccess } returns true

            val result = uut.calculateValidationErrors(dummyProject)

            result shouldHaveSize 0

            verify { transformationEvaluator.evaluate(dummyPath, jsonNodeMock) }
            verify { schemaMock.validate(jsonNodeMock) }
            verify { schemaService.loadSchema(dummyPath) }
            confirmVerified(transformationEvaluator, schemaMock)
        }

        "calculateNoopDowncastErrors - should fail on non existing schema" {
            val namespace = Namespace("foo", dummyPath, listOf(event1))
            val dummyProject = Project(null, listOf(namespace))

            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { schemaService.loadSchema(dummyPath) } returns Left(ProjectError.NoSchema(dummyPath))
            every { missingTransformationCalculator.calculateDowncastTransformations(event1) } returns listOf(
                Pair(version2, version1)
            )

            val result = uut.calculateNoopDowncastErrors(dummyProject)

            result shouldHaveSize 1
            result[0].shouldBeInstanceOf<ProjectError.NoSchema>()

            verify {
                schemaService.loadSchema(dummyPath)
            }
            verify {
                missingTransformationCalculator.calculateDowncastTransformations(event1)
            }
            confirmVerified(schemaService, missingTransformationCalculator)
        }

        "calculateNoopDowncastErrors - should fail for validation errors" {
            val namespace = Namespace("foo", dummyPath, listOf(event1))
            val dummyProject = Project(null, listOf(namespace))

            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { schemaService.loadSchema(dummyPath) } returns Right(schemaMock)
            every { schemaMock.validate(jsonNodeMock) } returns processingResultMock
            every { processingResultMock.isSuccess } returns false
            every { missingTransformationCalculator.calculateDowncastTransformations(event1) } returns listOf(
                Pair(version2, version1)
            )

            val result = uut.calculateNoopDowncastErrors(dummyProject)

            result shouldHaveSize 1
            result[0].shouldBeInstanceOf<ProjectError.NoDowncastForVersion>()

            verify { schemaMock.validate(jsonNodeMock) }
            verify { schemaService.loadSchema(dummyPath) }
            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }
            confirmVerified(schemaService, schemaMock, missingTransformationCalculator)
        }

        "calculateNoopDowncastErrors - should succeed" {
            val namespace = Namespace("foo", dummyPath, listOf(event1))
            val dummyProject = Project(null, listOf(namespace))

            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { schemaService.loadSchema(dummyPath) } returns Right(schemaMock)
            every { schemaMock.validate(jsonNodeMock) } returns processingResultMock
            every { processingResultMock.isSuccess } returns true
            every { missingTransformationCalculator.calculateDowncastTransformations(event1) } returns listOf(
                Pair(version2, version1)
            )

            val result = uut.calculateNoopDowncastErrors(dummyProject)

            result shouldHaveSize 0

            verify { schemaMock.validate(jsonNodeMock) }
            verify { schemaService.loadSchema(dummyPath) }
            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }
            confirmVerified(schemaService, schemaMock, missingTransformationCalculator)
        }
    }
}