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

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.annotations.VisibleForTesting
import org.factcast.schema.registry.cli.domain.*
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.utils.SchemaService
import org.factcast.schema.registry.cli.utils.mapEventTransformations
import org.factcast.schema.registry.cli.utils.mapEvents
import org.factcast.schema.registry.cli.validation.MissingTransformationCalculator
import org.factcast.schema.registry.cli.validation.ProjectError
import org.factcast.schema.registry.cli.validation.TransformationEvaluator
import org.factcast.schema.registry.cli.validation.validators.TransformationValidationService
import jakarta.inject.Singleton

@Singleton
class TransformationValidationServiceImpl(
    private val missingTransformationCalculator: MissingTransformationCalculator,
    private val fileSystemService: FileSystemService,
    private val schemaService: SchemaService,
    private val transformationEvaluator: TransformationEvaluator
) : TransformationValidationService {
    override fun validateTransformations(project: Project): List<ProjectError> {

        val downCastErrors = calculateNoopDowncastErrors(project)
        val missingUpCastTransformations = calculateMissingUpcastTransformations(project)
        val validationErrors = calculateValidationErrors(project)

        return downCastErrors
            .plus(missingUpCastTransformations)
            .plus(validationErrors)
    }

    @VisibleForTesting
    fun calculateValidationErrors(project: Project) = project.mapEventTransformations { ns, event, transformation ->
        val fromVersion = event.versions.find { it.version == transformation.from }
        val toVersion = event.versions.find { it.version == transformation.to }

        if (fromVersion == null || toVersion == null) {
            return@mapEventTransformations listOf(
                ProjectError.MissingVersionForTransformation(
                    transformation.from,
                    transformation.to,
                    transformation.transformationPath
                )
            )
        }


        val examples = fromVersion.examples
            .mapNotNull { fileSystemService.readToJsonNode(it.exampleFilePath) }

        schemaService
            .loadSchema(toVersion.schemaPath)
            .fold({ listOf(it) }) { schema ->
                examples.mapNotNull { example ->
                    val transformationResult: JsonNode?
                    try {
                        transformationResult = transformationEvaluator.evaluate(ns, event, transformation, example)
                    } catch (e: Exception) {
                        return@mapNotNull ProjectError.TransformationError(
                            event.type,
                            fromVersion.version,
                            toVersion.version,
                            e
                        )
                    }

                    transformationResult?.let {
                        val validationResult = schema.validate(it)
                        if (validationResult.isSuccess) {
                            null
                        } else {
                            ProjectError.TransformationValidationError(
                                event.type,
                                fromVersion.version,
                                toVersion.version,
                                validationResult
                            )
                        }
                    }
                }
            }
    }.flatten()

    @VisibleForTesting
    fun calculateMissingUpcastTransformations(project: Project): List<ProjectError> = project.mapEvents { _, event ->
        missingTransformationCalculator.calculateUpcastTransformations(event).map {
            ProjectError.NoUpcastForVersion(
                it.first.version,
                it.second.version,
                event.type
            )
        }
    }.flatten()

    @VisibleForTesting
    fun calculateNoopDowncastErrors(project: Project) =
        project.mapEvents { _, event ->
            missingTransformationCalculator.calculateDowncastTransformations(event).flatMap {
                val (fromVersion, toVersion) = it
                val examples = fromVersion.examples.mapNotNull { fileSystemService.readToJsonNode(it.exampleFilePath) }

                schemaService
                    .loadSchema(toVersion.schemaPath)
                    .fold({ listOf(it) }, { schema ->
                        examples.mapNotNull {
                            val result = schema.validate(it)

                            if (result.isSuccess) {
                                null
                            } else {
                                ProjectError.NoDowncastForVersion(
                                    fromVersion.version,
                                    toVersion.version,
                                    event.type,
                                    result
                                )
                            }
                        }
                    })
            }
        }.flatten()
}
