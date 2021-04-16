package org.factcast.schema.registry.cli.registry.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Paths
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.json.TitleFilterService
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.nio.file.Path

class IndexFileCalculatorImplTest : StringSpec() {
    val checksumService = mockk<ChecksumService>()
    val missingTransformationCalculator = mockk<MissingTransformationCalculator>()
    val fileSystemService = mockk<FileSystemService>()
    val titleFilterService = mockk<TitleFilterService>()

    val dummyPath = Paths.get(".")
    val dummyJson = JsonNodeFactory.instance.objectNode()
    val transformation1to2 = Transformation(1, 2, dummyPath)
    val version1 = Version(1, dummyPath, dummyPath, emptyList())
    val version2 = Version(2, dummyPath, dummyPath, emptyList())
    val event1 = Event("bar", dummyPath, listOf(version1, version2), listOf(transformation1to2))
    val namespace1 = Namespace("foo", dummyPath, listOf(event1))
    val dummyProject = Project(null, listOf(namespace1))

    val uut = IndexFileCalculatorImpl(checksumService, missingTransformationCalculator,
            fileSystemService, titleFilterService )

    init {
        "calculateIndex without filtering" {
            every { checksumService.createMd5Hash(any<Path>()) } returns "foo"
            every { missingTransformationCalculator.calculateDowncastTransformations(any()) } returns listOf(
                    Pair(
                            version2,
                            version1
                    )
            )

            val index = uut.calculateIndex(dummyProject, false)

            index.schemes shouldHaveSize 2
            verify(exactly = 3) { checksumService.createMd5Hash(any<Path>()) }

            index.transformations shouldHaveSize 2
            index.transformations.any { it.id.startsWith("synthetic") } shouldBe true
            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }

            confirmVerified(checksumService, missingTransformationCalculator, fileSystemService, titleFilterService)
        }


        "calculateIndex with stripped titles" {
            every { checksumService.createMd5Hash(any<JsonNode>()) } returns "foo"
            every { checksumService.createMd5Hash(any<Path>()) } returns "foo"
            every { fileSystemService.readToJsonNode(any())} returns dummyJson
            every { titleFilterService.filter(any())} returns dummyJson
            every { missingTransformationCalculator.calculateDowncastTransformations(any()) } returns listOf(
                Pair(
                    version2,
                    version1
                )
            )

            val index = uut.calculateIndex(dummyProject, true)

            index.schemes shouldHaveSize 2
            verify(exactly = 2) { fileSystemService.readToJsonNode(dummyPath) }
            verify(exactly = 2) { titleFilterService.filter(any()) }
            verify(exactly = 2) { checksumService.createMd5Hash(any<JsonNode>()) }

            index.transformations shouldHaveSize 2
            index.transformations.any { it.id.startsWith("synthetic") } shouldBe true
            verify { checksumService.createMd5Hash(any<Path>()) }
            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }

            confirmVerified(checksumService, missingTransformationCalculator, fileSystemService, titleFilterService)
        }
    }
}
