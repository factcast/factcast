package org.factcast.schema.registry.cli.registry.impl

import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.domain.Version
import org.factcast.schema.registry.cli.utils.ChecksumService
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import java.nio.file.Paths

class IndexFileCalculatorImplTest : StringSpec() {
    val checksumService = mockk<ChecksumService>()
    val missingTransformationCalculator = mockk<MissingTransformationCalculator>()

    val dummyPath = Paths.get(".")
    val transformation1to2 = Transformation(1, 2, dummyPath)
    val version1 = Version(1, dummyPath, dummyPath, emptyList())
    val version2 = Version(2, dummyPath, dummyPath, emptyList())
    val event1 = Event("bar", dummyPath, listOf(version1, version2), listOf(transformation1to2))
    val namespace1 = Namespace("foo", dummyPath, listOf(event1))
    val dummyProject = Project(null, listOf(namespace1))


    val uut = IndexFileCalculatorImpl(checksumService, missingTransformationCalculator)

    init {
        "calculateIndex" {
            every { checksumService.createMd5Hash(any()) } returns "foo"
            every { missingTransformationCalculator.calculateDowncastTransformations(any()) } returns listOf(
                Pair(
                    version2,
                    version1
                )
            )

            val index = uut.calculateIndex(dummyProject)

            index.schemes shouldHaveSize 2
            verify(exactly = 3) { checksumService.createMd5Hash(dummyPath) }

            index.transformations shouldHaveSize 2
            index.transformations.any { it.id.startsWith("synthetic") } shouldBe true
            verify { missingTransformationCalculator.calculateDowncastTransformations(event1) }

            confirmVerified(checksumService, missingTransformationCalculator)
        }
    }

}