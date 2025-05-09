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
package org.factcast.schema.registry.cli.validation.validators.impl

import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.utils.SchemaService
import org.factcast.schema.registry.cli.utils.mapEventVersions
import org.factcast.schema.registry.cli.validation.ProjectError
import org.factcast.schema.registry.cli.validation.validators.ExampleValidationService
import org.springframework.stereotype.Component

@Component
class ExampleValidationServiceImpl(
    private val fileSystemService: FileSystemService,
    private val schemaService: SchemaService
) : ExampleValidationService {
    override fun validateExamples(project: Project) =
        project.mapEventVersions { _, _, version ->
            schemaService.loadSchema(version.schemaPath).fold({ listOf(it) }, { schema ->
                version.examples.mapNotNull { example ->
                    val json = fileSystemService.readToJsonNode(example.exampleFilePath)
                        ?: return@mapNotNull ProjectError.NoSuchFile(
                            example.exampleFilePath
                        )

                    val result = schema.validate(json)

                    when (result.isSuccess) {
                        false -> ProjectError.ValidationError(
                            example.exampleFilePath,
                            result
                        )

                        true -> null
                    }
                }
            })
        }
            .flatten()
}
