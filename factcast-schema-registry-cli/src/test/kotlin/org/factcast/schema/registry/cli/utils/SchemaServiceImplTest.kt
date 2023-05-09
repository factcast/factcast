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
package org.factcast.schema.registry.cli.utils

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.core.exceptions.ProcessingException
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
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

            uut.loadSchema(dummyPath).shouldBeLeft().also {
                it.shouldBeInstanceOf<ProjectError.NoSuchFile>()
            }

            verifyAll {
                fs.readToJsonNode(dummyPath)
            }
        }

        "loadSchema for corrupted schema" {
            every { fs.readToJsonNode(dummyPath) } returns jsonNodeMock
            every { jsonSchemaFactory.getJsonSchema(any<JsonNode>()) } throws ProcessingException("")

            uut.loadSchema(dummyPath).shouldBeLeft().also {
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

            uut.loadSchema(dummyPath).shouldBeRight().also {
                it shouldBe schemaMock
            }

            verifyAll {
                fs.readToJsonNode(dummyPath)
                jsonSchemaFactory.getJsonSchema(any<JsonNode>())
            }
        }
    }
}
