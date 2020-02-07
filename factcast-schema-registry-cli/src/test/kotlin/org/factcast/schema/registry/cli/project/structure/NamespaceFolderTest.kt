package org.factcast.schema.registry.cli.project.structure

import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.specs.StringSpec
import io.kotlintest.shouldBe
import java.nio.file.Paths

class NamespaceFolderTest : StringSpec() {
    val path = Paths.get("namespaceA")
    val dummyPath = Paths.get(".")
    val eventFolder = EventFolder(dummyPath, emptyList(), dummyPath, emptyList())

    init {
        "toNamespace" {
            val namespaceFolder = NamespaceFolder(path, listOf(eventFolder), dummyPath)
            val ns = namespaceFolder.toNamespace()

            ns.name shouldBe "namespaceA"
            ns.descriptionPath shouldBe dummyPath
            ns.events shouldHaveSize 1
        }
    }

}