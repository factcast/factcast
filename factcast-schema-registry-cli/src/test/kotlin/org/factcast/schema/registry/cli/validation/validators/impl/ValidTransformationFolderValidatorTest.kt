package org.factcast.schema.registry.cli.validation.validators.impl

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.forAll
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table
import java.nio.file.Paths

class ValidTransformationFolderValidatorTest : StringSpec() {
    val uut = ValidTransformationFolderValidator()

    init {
        "isValid" {
            table(
                headers("path", "validity"),
                row(Paths.get("1-2"), true),
                row(Paths.get("1-v2"), false),
                row(Paths.get("v1-2"), false),
                row(Paths.get("1"), false),
                row(Paths.get("1-2-3"), false)
            ).forAll { path, valid ->
                uut.isValid(path, null) shouldBe valid
            }
        }
    }
}