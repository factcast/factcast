package org.factcast.schema.registry.cli.project.structure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class ProjectFolderTest : StringSpec() {
    val dummyPath = Paths.get(".")
    val namespaceFolder = NamespaceFolder(dummyPath, emptyList(), dummyPath)

    init {
        "toProject" {
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespaceFolder))

            val project = projectFolder.toProject()

            project.description shouldBe dummyPath
            project.namespaces shouldHaveSize 1
        }
    }
}
