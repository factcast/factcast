package org.factcast.schema.registry.cli.validation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.domain.Version
import java.nio.file.Paths

class MissingTransformationCalculatorImplTest : StringSpec() {
    val uut = MissingTransformationCalculatorImpl()

    val dummyPath = Paths.get(".")
    val version1 = Version(1, dummyPath, dummyPath, emptyList())
    val version2 = Version(2, dummyPath, dummyPath, emptyList())
    val version3 = Version(3, dummyPath, dummyPath, emptyList())
    val version5 = Version(5, dummyPath, dummyPath, emptyList())

    val transformation3to2 = Transformation(3, 2, dummyPath)
    val transformation2to1 = Transformation(2, 1, dummyPath)
    val transformation1to2 = Transformation(1, 2, dummyPath)
    val transformation2to3 = Transformation(2, 3, dummyPath)

    init {
        "should handle single version" {
            val event = Event("foo", dummyPath, listOf(version1), emptyList())

            uut.calculateDowncastTransformations(event) shouldHaveSize 0
            uut.calculateUpcastTransformations(event) shouldHaveSize 0
        }

        "should calculate missing downcast transformations" {
            val event = Event("foo", dummyPath, listOf(version1, version2, version3), emptyList())

            val result = uut.calculateDowncastTransformations(event)
            result shouldHaveSize 2
            result shouldContain Pair(version2, version1)
            result shouldContain Pair(version3, version2)
        }

        "should calculate missing downcast transformations 2" {
            val event = Event(
                "foo",
                dummyPath,
                listOf(version1, version2, version3),
                listOf(transformation3to2, transformation1to2, transformation2to3)
            )

            val result = uut.calculateDowncastTransformations(event)
            result shouldHaveSize 1
            result shouldContain Pair(version2, version1)
        }

        "should calculate missing downcast transformations 3" {
            val event = Event(
                "foo",
                dummyPath,
                listOf(version1, version2, version3),
                listOf(transformation3to2, transformation2to1, transformation1to2, transformation2to3)
            )

            val result = uut.calculateDowncastTransformations(event)
            result shouldHaveSize 0
        }

        "should calculate missing downcast transformations for gaps in versions" {
            val event = Event("foo", dummyPath, listOf(version3, version5), emptyList())

            val result = uut.calculateDowncastTransformations(event)
            result shouldHaveSize 1
            result shouldContain Pair(version5, version3)
        }

        "should calculate missing upcast transformations" {
            val event = Event("foo", dummyPath, listOf(version1, version2, version3), emptyList())

            val result = uut.calculateUpcastTransformations(event)
            result shouldHaveSize 2
            result shouldContain Pair(version1, version2)
            result shouldContain Pair(version2, version3)
        }

        "should calculate missing upcast transformations for gaps in versions" {
            val event = Event("foo", dummyPath, listOf(version3, version5), emptyList())

            val result = uut.calculateUpcastTransformations(event)
            result shouldHaveSize 1
            result shouldContain Pair(version3, version5)
        }

        "should calculate missing upcast transformations 2" {
            val event = Event(
                "foo",
                dummyPath,
                listOf(version1, version2, version3),
                listOf(transformation1to2, transformation3to2, transformation2to1)
            )

            val result = uut.calculateUpcastTransformations(event)
            result shouldHaveSize 1
            result shouldContain Pair(version2, version3)
        }

        "should calculate missing upcast transformations 3" {
            val event = Event(
                "foo",
                dummyPath,
                listOf(version1, version2, version3),
                listOf(transformation1to2, transformation2to3)
            )

            val result = uut.calculateUpcastTransformations(event)
            result shouldHaveSize 0
        }
    }
}
