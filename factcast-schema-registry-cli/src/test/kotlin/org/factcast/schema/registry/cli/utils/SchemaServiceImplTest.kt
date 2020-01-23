package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.assertions.arrow.either.shouldBeLeft
import io.kotlintest.assertions.arrow.either.shouldBeRight
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.specs.StringSpec
import io.kotlintest.shouldBe
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.validation.ProjectError
import java.nio.file.Paths

class SchemaServiceImplTest : StringSpec() {
    val fs = mockk<FileSystemService>()
    val jsonSchemaFactory = mockk<JsonSchemaFactory>()
    val schemaMock = mockk<JsonSchema>()
    val jsonNodeMock = mockk<JsonNode>()
    val dummyPath = Paths.get(".")

    val uut = SchemaServiceImpl(fs, jsonSchemaFactory)

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "loadSchema for invalid path" {
            every { fs.readToJsonNode(dummyPath) } returns null

            uut.loadSchema(dummyPath).shouldBeLeft {
                it.shouldBeInstanceOf<ProjectError.NoSuchFile>()
            }

            verifyAll {
                fs.readToJsonNode(dummyPath)
            }
        }

        "loadSchema for corrupted schema" {
            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { jsonSchemaFactory.getJsonSchema(any<JsonNode>()) } throws Exception("")

            uut.loadSchema(dummyPath).shouldBeLeft {
                it.shouldBeInstanceOf<ProjectError.CorruptedSchema>()
            }

            verifyAll {
                fs.readToJsonNode(dummyPath)
                jsonSchemaFactory.getJsonSchema(any<JsonNode>())
            }
        }

        "loadSchema for valid schema" {
            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { jsonSchemaFactory.getJsonSchema(any<JsonNode>()) } returns schemaMock

            uut.loadSchema(dummyPath).shouldBeRight {
                it shouldBe schemaMock
            }

            verifyAll {
                fs.readToJsonNode(dummyPath)
                jsonSchemaFactory.getJsonSchema(any<JsonNode>())
            }
        }
    }

}