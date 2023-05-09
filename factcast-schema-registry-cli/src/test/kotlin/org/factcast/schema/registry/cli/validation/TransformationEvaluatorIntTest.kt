package org.factcast.schema.registry.cli.validation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import org.factcast.core.subscription.TransformationException
import org.factcast.schema.registry.cli.domain.Event
import org.factcast.schema.registry.cli.domain.Namespace
import org.factcast.schema.registry.cli.domain.Transformation
import org.factcast.schema.registry.cli.fixture
import org.factcast.schema.registry.cli.fs.FileSystemService

@MicronautTest
class TransformationEvaluatorIntTest(private val uut: TransformationEvaluator, private val fs: FileSystemService) :
    StringSpec() {

    val ns = mockk<Namespace>()
    val event = mockk<Event>()
    val transformation = mockk<Transformation>()
    val input = fixture("transformation/event.json")
    val workingTransformation = fixture("transformation/working.js")
    val failingTransformation = fixture("transformation/failing.js")
    val skippedTransformation = fixture("transformation/skipped.js")

    init {
        "basic transformations" {
            every { ns.name } returns "ns"
            every { event.type } returns "type"
            every { transformation.from } returns 1
            every { transformation.to } returns 2
            every { transformation.transformationPath } returns workingTransformation

            val data = fs.readToJsonNode(input)
            val output = uut.evaluate(ns, event, transformation, data!!)!!

            output["hobbies"].isArray shouldBe true
            output["hobbies"][0].asText() shouldBe "ABC"
            output["displayName"].asText() shouldBe "Hugo Egon"
            output["hobby"].isNull shouldBe true
        }

        "failing" {
            every { ns.name } returns "ns"
            every { event.type } returns "type"
            every { transformation.from } returns 1
            every { transformation.to } returns 2
            every { transformation.transformationPath } returns failingTransformation

            val data = fs.readToJsonNode(input)

            shouldThrow<TransformationException> { uut.evaluate(ns, event, transformation, data!!) }
        }

        "skipped" {
            every { ns.name } returns "ns"
            every { event.type } returns "type"
            every { transformation.from } returns 1
            every { transformation.to } returns 2
            every { transformation.transformationPath } returns skippedTransformation

            val data = fs.readToJsonNode(input)

            uut.evaluate(ns, event, transformation, data!!).shouldBeNull()
        }
    }
}