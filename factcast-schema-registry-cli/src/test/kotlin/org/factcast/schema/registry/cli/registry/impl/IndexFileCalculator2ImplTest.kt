package org.factcast.schema.registry.cli.registry.impl

import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.fixture
import org.factcast.schema.registry.cli.registry.index.FileBasedTransformation
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.nio.file.Path

class IndexFileCalculator2ImplTest : StringSpec() {
    val checksumService = mockk<ChecksumService>()
    val missingTransformationCalculator = mockk<MissingTransformationCalculator>()

    val uut = IndexFileCalculator2Impl(checksumService, missingTransformationCalculator)

    init {
        "calculateIndex" {
            every { checksumService.createMd5Hash(any()) } returns "foo"

            val testDistribution = fixture("registry-distribution")

            val index = uut.calculateIndex(testDistribution)

            index.schemes shouldHaveSize 3
            index.transformations shouldHaveSize 2

            verify(exactly = 5) { checksumService.createMd5Hash(any()) }
            confirmVerified(checksumService)
        }

        "map to schema" {
            every { checksumService.createMd5Hash(any()) } returns "foo"

            val schema = uut.toSchema(Path.of("foo", "bar", "namespace1", "EventA", "1", "schema.json"))
            schema.id shouldBe "namespace1/EventA/1/schema.json"
            schema.ns shouldBe "namespace1"
            schema.type shouldBe "EventA"
            schema.version shouldBe 1
            schema.hash shouldBe "foo"
        }

        "map to file based transformation" {
            every { checksumService.createMd5Hash(any()) } returns "foo"

            val transformation = uut.toFileBasedTransformation(Path.of("foo", "bar", "namespace2", "EventB", "1-2", "transform.js"))

            transformation.id shouldBe "namespace2/EventB/1-2/transform.js"
            transformation.ns shouldBe "namespace2"
            transformation.type shouldBe "EventB"
            transformation.from shouldBe 1
            transformation.to shouldBe 2
            transformation.hash shouldBe "foo"
        }
    }
}
