package org.factcast.schema.registry.cli.project.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class TransformationFolderTest : StringSpec() {

    init {
        "toTransformation" {
            val path = Paths.get("1-2")
            val dummyPath = Paths.get(".")

            val transformationFolder = TransformationFolder(path, dummyPath)
            val transformation = transformationFolder.toTransformation()

            transformation.from shouldBe 1
            transformation.to shouldBe 2

            transformation.transformationPath shouldBe dummyPath
        }
    }
}
