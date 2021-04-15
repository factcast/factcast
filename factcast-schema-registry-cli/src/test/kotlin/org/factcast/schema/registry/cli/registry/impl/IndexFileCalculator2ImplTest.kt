package org.factcast.schema.registry.cli.registry.impl

import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
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
            val firstSchema = index.schemes[0]
            firstSchema.id shouldBe "namespace1/EventA/1/schema.json"
            firstSchema.ns shouldBe "namespace1"
            firstSchema.type shouldBe "EventA"
            firstSchema.version shouldBe 1
            firstSchema.hash shouldBe "foo"

            // TODO how to verify just a few?
            verifyAll {
                checksumService.createMd5Hash(Path.of(testDistribution.toString(), "/namespace1/EventA/1/schema.json"))
                checksumService.createMd5Hash(Path.of(testDistribution.toString(), "/namespace1/EventB/1/schema.json"))
                checksumService.createMd5Hash(Path.of(testDistribution.toString(), "/namespace1/EventB/1/schema.json"))
            }

            index.transformations shouldHaveSize 2
            val firstTransformation = index.transformations[0] as FileBasedTransformation
            firstTransformation.id shouldBe "namespace2/EventB/1-2/transform.js"
            firstTransformation.ns shouldBe "namespace2"
            firstTransformation.type shouldBe "EventB"
            firstTransformation.from shouldBe 1
            firstTransformation.to shouldBe 2
            firstTransformation.hash shouldBe "foo"

            confirmVerified(checksumService)
        }


//        "calculateIndex" {
//            every { checksumService.createMd5Hash(any()) } returns "foo"
//            every { missingTransformationCalculator.calculateDowncastTransformations(any()) } returns listOf(
//                Pair(
//                    version2,
//                    version1
//                )
//            )
//
//            val index = uut.calculateIndex(dummyProject)
//
//            index.schemes shouldHaveSize 2
//            verify(exactly = 3) { checksumService.createMd5Hash(dummyPath) }
//
//            index.transformations shouldHaveSize 2
//            index.transformations.any { it.id.startsWith("synthetic") } shouldBe true
//            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }
//
//            confirmVerified(checksumService, missingTransformationCalculator)
//        }
    }
}
