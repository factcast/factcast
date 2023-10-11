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
package org.factcast.schema.registry.cli.validation

import arrow.core.Either
import arrow.core.flatMap
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.validation.validators.ExampleValidationService
import org.factcast.schema.registry.cli.validation.validators.ProjectStructureValidationService
import org.factcast.schema.registry.cli.validation.validators.TransformationValidationService
import jakarta.inject.Singleton

@Singleton
class ValidationServiceImpl(
    private val exampleValidationService: ExampleValidationService,
    private val transformationValidationService: TransformationValidationService,
    private val projectStructureValidationService: ProjectStructureValidationService
) : ValidationService {
    override fun validateProject(projectFolder: ProjectFolder): Either<List<ProjectError>, Project> {
        return projectStructureValidationService.validateProjectStructure(projectFolder)
            .flatMap {
                val errors = exampleValidationService
                    .validateExamples(it)
                    .plus(transformationValidationService.validateTransformations(it))

                if (errors.isEmpty()) {
                    Either.Right(it)
                } else {
                    Either.Left(errors)
                }
            }
    }
}
