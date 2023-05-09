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
import javax.validation.ConstraintValidatorContext

class ValidVersionFolderValidatorTest : StringSpec() {
    val uut = ValidVersionFolderValidator()
    val ctx = mockk<ConstraintValidatorContext>()

    init {
        "isValid" {
            every { ctx.defaultConstraintMessageTemplate } returns "foo"

            table(
                headers("path", "validity"),
                row(Paths.get("1"), true),
                row(Paths.get("1.2"), false),
                row(Paths.get("bar"), false)
            ).forAll { path, valid ->
                uut.isValid(path, ctx) shouldBe valid
            }
        }
    }
}
