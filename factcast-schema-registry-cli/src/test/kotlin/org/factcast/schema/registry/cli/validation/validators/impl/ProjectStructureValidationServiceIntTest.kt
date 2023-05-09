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
package org.factcast.schema.registry.cli.validation.validators.impl

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.project.structure.TransformationFolder
import org.factcast.schema.registry.cli.validation.ProjectError
import org.factcast.schema.registry.cli.validation.validators.ProjectStructureValidationService
import java.nio.file.Paths

@MicronautTest
class ProjectStructureValidationServiceIntTest(private val uut: ProjectStructureValidationService) : StringSpec() {
    val dummyPath = Paths.get(".")

    init {
        "validateProjectStructure - Projectfolder" {

            val projectFolder = ProjectFolder(dummyPath, null, emptyList())

            val result = uut.validateProjectStructure(projectFolder)
            result.shouldBeLeft().also {
                it.filterIsInstance<ProjectError.NoNamespaces>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.NoDescription>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - NamespaceFolder" {
            val namespace = NamespaceFolder(dummyPath, emptyList(), null)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)
            result.shouldBeLeft().also {
                it.filterIsInstance<ProjectError.NoEvents>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.NoDescription>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - EventFolder" {
            val eventFolder = EventFolder(dummyPath, emptyList(), null, emptyList())
            val namespace = NamespaceFolder(dummyPath, listOf(eventFolder), dummyPath)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeLeft().also {
                it.filterIsInstance<ProjectError.NoEventVersions>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.NoDescription>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - EventVersionFolder" {
            val eventVersionFolder = EventVersionFolder(dummyPath, null, null, emptyList())
            val eventFolder = EventFolder(dummyPath, listOf(eventVersionFolder), dummyPath, emptyList())
            val namespace = NamespaceFolder(dummyPath, listOf(eventFolder), dummyPath)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeLeft().also {
                it.filterIsInstance<ProjectError.NoSchema>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.NoDescription>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.NoExamples>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - invalid event version" {
            val eventPath = Paths.get("eventA")
            val namespacePath = Paths.get("namespaceA")
            val versionPath = Paths.get("v1")
            val eventVersionFolder = EventVersionFolder(versionPath, dummyPath, dummyPath, listOf(dummyPath))
            val eventFolder = EventFolder(eventPath, listOf(eventVersionFolder), dummyPath, emptyList())
            val namespace = NamespaceFolder(namespacePath, listOf(eventFolder), dummyPath)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeLeft().also {
                it.filterIsInstance<ProjectError.WrongVersionFormat>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - invalid transformation version" {
            val eventPath = Paths.get("eventA")
            val namespacePath = Paths.get("namespaceA")
            val versionPath = Paths.get("1")
            val transformationPath = Paths.get("1-v2")
            val transformationFolder = TransformationFolder(transformationPath, null)
            val eventVersionFolder = EventVersionFolder(versionPath, dummyPath, dummyPath, listOf(dummyPath))
            val eventFolder =
                EventFolder(eventPath, listOf(eventVersionFolder), dummyPath, listOf(transformationFolder))
            val namespace = NamespaceFolder(namespacePath, listOf(eventFolder), dummyPath)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeLeft().also {
                it.filterIsInstance<ProjectError.NoSuchFile>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.WrongVersionFormat>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - should return valid project" {
            val eventPath = Paths.get("eventA")
            val namespacePath = Paths.get("namespaceA")
            val versionPath = Paths.get("1")
            val eventVersionFolder = EventVersionFolder(versionPath, dummyPath, dummyPath, listOf(dummyPath))
            val eventFolder = EventFolder(eventPath, listOf(eventVersionFolder), dummyPath, emptyList())
            val namespace = NamespaceFolder(namespacePath, listOf(eventFolder), dummyPath)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeRight().also {
                it.shouldBeInstanceOf<Project>()
            }
        }
    }
}
