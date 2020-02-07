package org.factcast.schema.registry.cli.registry.impl

import io.kotlintest.specs.StringSpec
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.registry.FactcastIndexCreator
import org.factcast.schema.registry.cli.registry.HugoPageCreator
import java.nio.file.Paths

class DistributionCreatorServiceImplTest : StringSpec() {
    val hugoPackageCreator = mockk<HugoPageCreator>()
    val factcastIndexCreator = mockk<FactcastIndexCreator>()
    val dummyPath = Paths.get(".")
    val dummyProject = Project(dummyPath, emptyList())

    val uut = DistributionCreatorServiceImpl(hugoPackageCreator, factcastIndexCreator)

    init {
        "createDistributable" {
            every { hugoPackageCreator.creteHugoPage(dummyPath, dummyProject) } returns Unit
            every { factcastIndexCreator.createFactcastIndex(any(), dummyProject) } returns Unit

            uut.createDistributable(dummyPath, dummyProject)

            verify { hugoPackageCreator.creteHugoPage(dummyPath, dummyProject) }

            verify {
                factcastIndexCreator.createFactcastIndex(match { it.endsWith("static/registry") }, dummyProject)
            }

            confirmVerified(hugoPackageCreator, factcastIndexCreator)
        }
    }

}