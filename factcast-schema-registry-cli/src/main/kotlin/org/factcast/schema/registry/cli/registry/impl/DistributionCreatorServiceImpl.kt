/*
 * Copyright © 2017-2020 factcast.org
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

import java.nio.file.Path
import java.nio.file.Paths
import jakarta.inject.Singleton
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.registry.DistributionCreatorService
import org.factcast.schema.registry.cli.registry.FactcastIndexCreator
import org.factcast.schema.registry.cli.registry.StaticPageCreator

@Singleton
class DistributionCreatorServiceImpl(
    private val staticPageCreator: StaticPageCreator,
    private val factcastIndexCreator: FactcastIndexCreator
) : DistributionCreatorService {
    override fun createDistributable(outputPath: Path, project: Project, removedSchemaProps: Set<String>) {
        staticPageCreator.createPage(outputPath, project)

        val indexPath = outputPath.resolve(Paths.get("static", "registry"))
        factcastIndexCreator.createFactcastIndex(indexPath, project, removedSchemaProps)
    }
}
