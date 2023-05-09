/*
 * Copyright © 2017-2023 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.schema.registry.cli.registry.impl

import io.kotest.core.spec.style.StringSpec
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.registry.FactcastIndexCreator
import org.factcast.schema.registry.cli.registry.StaticPageCreator
import java.nio.file.Paths

class DistributionCreatorServiceImplTest : StringSpec() {
    val hugoPackageCreator = mockk<StaticPageCreator>()
    val factcastIndexCreator = mockk<FactcastIndexCreator>()
    val dummyPath = Paths.get(".")
    val dummyProject = Project(dummyPath, emptyList())

    val uut = DistributionCreatorServiceImpl(hugoPackageCreator, factcastIndexCreator)

    init {
        "createDistributable" {
            every { hugoPackageCreator.createPage(dummyPath, dummyProject) } returns Unit
            every { factcastIndexCreator.createFactcastIndex(any(), dummyProject) } returns Unit

            uut.createDistributable(dummyPath, dummyProject)

            verify { hugoPackageCreator.createPage(dummyPath, dummyProject) }

            verify {
                factcastIndexCreator.createFactcastIndex(match { it.endsWith("static/registry") }, dummyProject)
            }

            confirmVerified(hugoPackageCreator, factcastIndexCreator)
        }
    }
}
