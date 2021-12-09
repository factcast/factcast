package org.factcast.schema.registry.cli.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.paths.shouldBeEmptyDirectory
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.paths.shouldNotBeEmptyDirectory
import io.kotest.matchers.shouldBe
import org.apache.commons.io.FileUtils
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText

class UnzipUtilsTest : StringSpec() {

    init {
        "extracts zip file from input stream" {

            // we need a temporary target dir
            val tempDir = createTempDirectory()

            tempDir.shouldBeEmptyDirectory()

            try {

                UnzipUtilsTest::class.java.getResourceAsStream("/sampleFile.zip")?.use { it ->
                    println("Inflating")
                    UnzipUtils.unzip(it, tempDir)
                }

                tempDir.shouldNotBeEmptyDirectory()
                tempDir.shouldContainFile("rootFile")

                tempDir.resolve("emptyDir").shouldBeEmptyDirectory()

                tempDir.resolve("nonEmptyDir").shouldBeADirectory()
                tempDir.resolve("nonEmptyDir").shouldNotBeEmptyDirectory()
                tempDir.resolve("nonEmptyDir").shouldContainFile("subDirFile")

                tempDir.resolve("rootFile").readText() shouldBe "CONTENT1\n"
                tempDir.resolve("nonEmptyDir/subDirFile").readText() shouldBe "CONTENT2\n"

            } finally {
                FileUtils.deleteQuietly(tempDir.toFile())
            }
        }
    }

}
