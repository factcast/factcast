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

import arrow.core.Either
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.project.structure.Folder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.project.structure.toProject
import org.factcast.schema.registry.cli.validation.*
import org.factcast.schema.registry.cli.validation.validators.ProjectStructureValidationService
import jakarta.inject.Singleton
import jakarta.validation.Validator

@Singleton
class ProjectStructureValidationServiceImpl(private val validator: Validator) : ProjectStructureValidationService {
    override fun validateProjectStructure(projectFolder: ProjectFolder): Either<List<ProjectError>, Project> {
        val errors = validator
            .validate(projectFolder)
            .map {
                when (it.messageTemplate) {
                    NO_DESCRIPTION ->
                        ProjectError.NoDescription(it.leafBean.toFolder().path)

                    NO_EVENT_VERSIONS ->
                        ProjectError.NoEventVersions(it.leafBean.toFolder().path)

                    NO_SCHEMA ->
                        ProjectError.NoSchema(it.leafBean.toFolder().path)

                    NO_EXAMPLES ->
                        ProjectError.NoExamples(it.leafBean.toFolder().path)

                    NO_EVENTS ->
                        ProjectError.NoEvents(it.leafBean.toFolder().path)

                    NO_NAMESPACES ->
                        ProjectError.NoNamespaces(it.leafBean.toFolder().path)

                    NO_TRANSFORMATION_FILE ->
                        ProjectError.NoSuchFile(it.leafBean.toFolder().path)

                    TRANSFORMATION_VERSION_INVALID, VERSION_INVALID -> {
                        val folder = it.leafBean.toFolder()
                        ProjectError.WrongVersionFormat(folder.path.fileName.toString(), folder.path)
                    }

                    else ->
                        throw IllegalArgumentException("Unknown error type: ${it.messageTemplate}")
                }
            }

        return if (errors.isNotEmpty())
            Either.Left(errors)
        else {
            Either.Right(
                projectFolder.toProject()
            )
        }
    }
}

fun Any.toFolder() = this as Folder
