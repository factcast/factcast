package org.factcast.schema.registry.cli.project.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class EventFolderKtTest : StringSpec() {
    val path = Paths.get("eventA")
    val descriptionPath = Paths.get("index.md")

    init {
        "toEvent" {
            val eventFolder = EventFolder(path, emptyList(), descriptionPath, emptyList())
            val event = eventFolder.toEvent()

            event.type shouldBe "eventA"
            event.descriptionPath shouldBe descriptionPath
        }
    }
}
