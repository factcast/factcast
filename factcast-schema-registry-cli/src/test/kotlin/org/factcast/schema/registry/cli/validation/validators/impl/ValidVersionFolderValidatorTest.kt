package org.factcast.schema.registry.cli.validation.validators.impl

import io.kotlintest.specs.StringSpec
import io.kotlintest.shouldBe
import io.kotlintest.tables.forAll
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table
import org.factcast.schema.registry.cli.validation.validators.ValidVersionFolder
import org.factcast.schema.registry.cli.validation.validators.impl.ValidVersionFolderValidator
import java.nio.file.Path
import java.nio.file.Paths

class ValidVersionFolderValidatorTest : StringSpec() {
    val uut = ValidVersionFolderValidator()

    init {
        "isValid" {
            table(
                headers("path", "validity"),
                row(Paths.get("1"), true),
                row(Paths.get("1.2"), false),
                row(Paths.get("bar"), false)
            ).forAll { path, valid ->
                uut.isValid(path, null) shouldBe valid
            }
        }
    }
}