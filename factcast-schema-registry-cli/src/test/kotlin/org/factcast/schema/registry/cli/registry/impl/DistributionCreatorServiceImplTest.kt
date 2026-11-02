package org.factcast.schema.registry.cli.registry.impl

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.registry.FactcastIndexCreator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

class DistributionCreatorServiceImplTest : StringSpec() {
    val factcastIndexCreator = mockk<FactcastIndexCreator>()
    val dummyProject = Project(Paths.get("."), emptyList())

    val uut = DistributionCreatorServiceImpl(factcastIndexCreator)

    init {
        "createDistributable packages registry files beside the index" {
            val output = Files.createTempDirectory("registry-build-test-")
            try {
                every { factcastIndexCreator.createFactcastIndex(any(), dummyProject) } answers {
                    val registry = firstArg<Path>()
                    Files.createDirectories(registry.resolve("ns/type/1"))
                    Files.writeString(registry.resolve("index.json"), "index")
                    Files.writeString(registry.resolve("ns/type/1/schema.json"), "schema")
                }

                uut.createDistributable(output, dummyProject)

                verify {
                    factcastIndexCreator.createFactcastIndex(match { it.endsWith("static/registry") }, dummyProject)
                }
                ZipFile(output.resolve("static/registry/registry.zip").toFile()).use { zip ->
                    zip.entries().asSequence().map { it.name }.toSet() shouldBe
                        setOf("index.json", "ns/type/1/schema.json")
                    zip.getInputStream(zip.getEntry("index.json")).bufferedReader().readText() shouldBe "index"
                    zip.getInputStream(zip.getEntry("ns/type/1/schema.json"))
                        .bufferedReader().readText() shouldBe "schema"
                }
            } finally {
                output.toFile().deleteRecursively()
            }
        }
    }
}
