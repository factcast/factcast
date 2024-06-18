package org.factcast.schema.registry.cli.validation.validators.impl

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import jakarta.validation.ConstraintValidatorContext

class ValidTransformationFolderValidatorTest : StringSpec() {
    val uut = ValidTransformationFolderValidator()

    val ctx = mockk<ConstraintValidatorContext>()

    init {
        "isValid" {
            every { ctx.defaultConstraintMessageTemplate } returns "foo"

            table(
                headers("path", "validity"),
                row(Paths.get("1-2"), true),
                row(Paths.get("1-v2"), false),
                row(Paths.get("v1-2"), false),
                row(Paths.get("1"), false),
                row(Paths.get("1-2-3"), false)
            ).forAll { path, valid ->
                uut.isValid(path, ctx) shouldBe valid
            }
        }
    }
}
