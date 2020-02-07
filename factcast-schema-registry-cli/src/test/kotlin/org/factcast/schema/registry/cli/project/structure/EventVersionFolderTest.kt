package org.factcast.schema.registry.cli.project.structure

import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.specs.StringSpec
import io.kotlintest.shouldBe
import java.nio.file.Paths

class EventVersionFolderTest : StringSpec() {
    val path = Paths.get("1")
    val dummyPath = Paths.get(".")
    val examplePath = Paths.get("example.json")

    init {
        "toEventVersion" {
            val versionFolder = EventVersionFolder(path, dummyPath, dummyPath, listOf(examplePath))
            val eventVersion = versionFolder.toEventVersion()

            eventVersion.version shouldBe 1
            eventVersion.descriptionPath shouldBe dummyPath
            eventVersion.schemaPath shouldBe dummyPath
            eventVersion.examples shouldHaveSize 1
            eventVersion.examples[0].name shouldBe "example.json"
            eventVersion.examples[0].exampleFilePath shouldBe examplePath
        }
    }

}