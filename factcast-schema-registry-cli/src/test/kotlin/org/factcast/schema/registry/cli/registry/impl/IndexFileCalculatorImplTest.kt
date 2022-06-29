package org.factcast.schema.registry.cli.registry.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.*
import org.factcast.schema.registry.cli.fixture
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.fs.FileSystemServiceImpl
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.utils.ChecksumServiceImpl
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.nio.file.Path
import java.nio.file.Paths

class IndexFileCalculatorImplTest : StringSpec() {
    val checksumService = mockk<ChecksumService>()
    val missingTransformationCalculator = mockk<MissingTransformationCalculator>()
    val fileSystemService = mockk<FileSystemService>()

    val dummyPath = Paths.get(".")
    val dummyJson = JsonNodeFactory.instance.objectNode()
    val transformation1to2 = Transformation(1, 2, dummyPath)
    val version1 = Version(1, dummyPath, dummyPath, emptyList())
    val version2 = Version(2, dummyPath, dummyPath, emptyList())
    val event1 = Event("bar", dummyPath, listOf(version1, version2), listOf(transformation1to2))
    val namespace1 = Namespace("foo", dummyPath, listOf(event1))
    val dummyProject = Project(null, listOf(namespace1))

    val uut = IndexFileCalculatorImpl(
        checksumService, missingTransformationCalculator,
        fileSystemService
    )

    init {
        "calculateIndex without filtering" {
            every { checksumService.createMd5Hash(any<Path>()) } returns "foo"
            every { missingTransformationCalculator.calculateDowncastTransformations(any()) } returns listOf(
                Pair(
                    version2,
                    version1
                )
            )

            val index = uut.calculateIndex(dummyProject, emptySet())

            index.schemes shouldHaveSize 2
            verify(exactly = 3) { checksumService.createMd5Hash(any<Path>()) }

            index.transformations shouldHaveSize 2
            index.transformations.any { it.id.startsWith("synthetic") } shouldBe true
            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }

            confirmVerified(checksumService, missingTransformationCalculator, fileSystemService)
        }

        "calculateIndex with stripped titles" {
            every { checksumService.createMd5Hash(any<JsonNode>()) } returns "foo"
            every { checksumService.createMd5Hash(any<Path>()) } returns "foo"
            every { fileSystemService.readToJsonNode(any()) } returns dummyJson
            every { missingTransformationCalculator.calculateDowncastTransformations(any()) } returns listOf(
                Pair(
                    version2,
                    version1
                )
            )

            val index = uut.calculateIndex(dummyProject, setOf("title"))

            index.schemes shouldHaveSize 2
            verify(exactly = 2) { fileSystemService.readToJsonNode(dummyPath) }
            verify(exactly = 2) { checksumService.createMd5Hash(any<JsonNode>()) }

            index.transformations shouldHaveSize 2
            index.transformations.any { it.id.startsWith("synthetic") } shouldBe true
            verify { checksumService.createMd5Hash(any<Path>()) }
            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }

            confirmVerified(checksumService, missingTransformationCalculator, fileSystemService)
        }

        "semantically identical JSON strings have same hash" {
            val mapper = ObjectMapper()

            val jsonWithSameHashCode = listOf(
                """{"type":"string","foo1":"bar1", "foo2":"bar2"}""",
                """{   "type"   :   "string",   "foo1"   :   "bar1",   "foo2"   :   "bar2"}""",
                """{"type":"string","foo1":"bar1", "foo2":"bar2","title":"title"}""",
                """{   "type"   :   "string",   "foo1"   :   "bar1",   "foo2"   :   "bar2",   "title"   :   "title"}""",
            )

            jsonWithSameHashCode.forEach { jsonString ->
                val jsonNode = mapper.readTree(jsonString)
                every { fileSystemService.readToJsonNode(any()) } returns jsonNode

                val checksumService = ChecksumServiceImpl(fileSystemService, ObjectMapper())
                val uut = IndexFileCalculatorImpl(
                    checksumService, missingTransformationCalculator,
                    fileSystemService
                )
                val result = uut.createFilteredMd5Hash(Paths.get("some/file"), setOf("title"))

                result shouldBe "c98fec7850d52fa8eda4c3fd8ff4d8f8"
            }
        }

        "schema hash differs when schemaStripTitle is enabled" {
            val schemaPath = fixture("schema.json")
            val dummyPath = Paths.get(".")
            val version = Version(1, schemaPath, dummyPath, emptyList())
            val event = Event("bar", dummyPath, listOf(version), emptyList())
            val namespace = Namespace("foo", dummyPath, listOf(event))
            val dummyProject = Project(null, listOf(namespace))

            val fileSystemService = FileSystemServiceImpl()
            val checksumService = ChecksumServiceImpl(fileSystemService, ObjectMapper())
            val uut = IndexFileCalculatorImpl(
                checksumService, missingTransformationCalculator,
                fileSystemService
            )

            val indexWithoutStrippedTitles = uut.calculateIndex(dummyProject, emptySet())
            val indexWithStrippedTitles = uut.calculateIndex(dummyProject, setOf("title"))

            indexWithoutStrippedTitles.schemes[0].hash shouldBe "f560b3a9a93014b6939f100ce187641b"
            indexWithStrippedTitles.schemes[0].hash shouldBe "26e0e35414d1c5cecac62eb900b50efc"
        }
    }
}
