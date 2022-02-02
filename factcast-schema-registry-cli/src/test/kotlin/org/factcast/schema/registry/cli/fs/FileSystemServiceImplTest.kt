package org.factcast.schema.registry.cli.fs

import com.fasterxml.jackson.databind.JsonNode
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.factcast.schema.registry.cli.fixture
import java.nio.file.Files
import java.nio.file.Paths

class FileSystemServiceImplTest : StringSpec() {
    var tmp = Files.createTempDirectory("fc-test")
    val uut = FileSystemServiceImpl()

    override fun afterTest(testCase: TestCase, result: TestResult) {
        try {
            Files.delete(tmp)
        } catch (e: Exception) {
        } finally {
            tmp = Files.createTempDirectory("fx-test")
        }
    }

    init {
        "exists" {
            uut.exists(fixture("schema.json")) shouldBe true
            uut.exists(fixture("nope.json")) shouldBe false
        }

        "listDirectories" {
            uut.listDirectories(fixture("")) shouldContain fixture("sample-folder")
            uut.listDirectories(fixture("sample-folder")) shouldHaveSize 0
        }

        "listFiles" {
            val files = uut.listFiles(fixture(""))
            files shouldHaveSize 1
            files shouldContain fixture("schema.json")
        }

        "ensureDirectories" {
            val outputPath = Paths.get(tmp.toString(), "foo")
            uut.ensureDirectories(outputPath)

            uut.exists(outputPath) shouldBe true
        }

        "writeToFile" {
            val outputPath = Paths.get(tmp.toString(), "test.txt")
            uut.writeToFile(outputPath.toFile(), "bar")

            uut.exists(outputPath) shouldBe true
        }

        "readToString" {
            uut.readToString(fixture("schema.json").toFile()) shouldContain "firstName"
        }

        "readToStrings" {
            val output = uut.readToStrings(fixture("schema.json").toFile())
            output[1] shouldContain "additionalProperties"
            output[8] shouldContain "required"
        }

        "copyFile" {
            val outputPath = Paths.get(tmp.toString(), "schema.json")

            uut.copyFile(fixture("schema.json").toFile(), outputPath.toFile())

            uut.exists(outputPath)
        }

        "readToJsonNode" {
            uut.readToJsonNode(fixture("schema.json")).shouldBeInstanceOf<JsonNode>()
            uut.readToJsonNode(fixture("nope.json")) shouldBe null
        }

        "deleteDirectory" {
            uut.exists(tmp) shouldBe true

            uut.deleteDirectory(tmp)

            uut.exists(tmp) shouldBe false
        }

        "readToBytes" {
            val exampleFile = fixture("schema.json")

            uut.readToBytes(exampleFile) shouldBe uut.readToString(exampleFile.toFile()).toByteArray()
        }

        "copyDirectory" {
            val outputPath = Paths.get(tmp.toString(), "foo")

            uut.exists(outputPath) shouldBe false

            uut.copyDirectory(fixture(""), outputPath)

            uut.exists(outputPath) shouldBe true
        }

        "copyFilteredJson" {
            val outputPath = Paths.get(tmp.toString(), "test.txt")

            uut.copyFilteredJson(
                fixture("schema.json").toFile(),
                outputPath.toFile(),
                setOf("title")
            )

            uut.exists(outputPath) shouldBe true
            uut.readToString(outputPath.toFile()) shouldNotContain "title"
        }
    }
}
