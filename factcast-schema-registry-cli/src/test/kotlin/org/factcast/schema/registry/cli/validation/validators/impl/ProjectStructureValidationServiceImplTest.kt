package org.factcast.schema.registry.cli.validation.validators.impl

import io.kotlintest.Spec
import io.kotlintest.assertions.arrow.either.shouldBeLeft
import io.kotlintest.assertions.arrow.either.shouldBeRight
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.validation.validator.DefaultValidator
import io.micronaut.validation.validator.DefaultValidatorConfiguration
import io.micronaut.validation.validator.Validator
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.DefaultConstraintValidators
import org.factcast.schema.registry.cli.domain.Project
import org.factcast.schema.registry.cli.project.structure.EventFolder
import org.factcast.schema.registry.cli.project.structure.EventVersionFolder
import org.factcast.schema.registry.cli.project.structure.NamespaceFolder
import org.factcast.schema.registry.cli.project.structure.ProjectFolder
import org.factcast.schema.registry.cli.project.structure.TransformationFolder
import org.factcast.schema.registry.cli.validation.ProjectError
import org.factcast.schema.registry.cli.validation.validators.ProjectStructureValidationService
import org.factcast.schema.registry.cli.validation.validators.ValidTransformationFolder
import org.factcast.schema.registry.cli.validation.validators.ValidVersionFolder
import java.nio.file.Paths
import java.util.*

class ProjectStructureValidationServiceImplTest : StringSpec() {
    val dummyPath = Paths.get(".")
    lateinit var context: ApplicationContext
    lateinit var uut: ProjectStructureValidationService

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)

        // Probably we should use MicronautTest here but the kotlintest extension is currently broken
        // so we create our own application context in order to get a Validator
        context = ApplicationContext.run()

        val validator = context.getBean(Validator::class.java)
        uut = ProjectStructureValidationServiceImpl(validator)
    }

    override fun afterSpec(spec: Spec) {
        super.afterSpec(spec)

        context.stop()
    }

    init {
        "validateProjectStructure - Projectfolder" {

            val projectFolder = ProjectFolder(dummyPath, null, emptyList())

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeLeft {
                it.filterIsInstance<ProjectError.NoNamespaces>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.NoDescription>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - NamespaceFolder" {
            val namespace = NamespaceFolder(dummyPath, emptyList(), null)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeLeft {
                it.filterIsInstance<ProjectError.NoEvents>() shouldHaveSize 1
                it.filterIsInstance<ProjectError.NoDescription>() shouldHaveSize 1
            }
        }

        "validateProjectStructure - EventFolder" {
            val eventFolder = EventFolder(dummyPath, emptyList(), null, emptyList())
            val namespace = NamespaceFolder(dummyPath, listOf(eventFolder), dummyPath)
            val projectFolder = ProjectFolder(dummyPath, dummyPath, listOf(namespace))

            val result = uut.validateProjectStructure(projectFolder)

            result.shouldBeLeft {
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

            result.shouldBeLeft {
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

            result.shouldBeLeft {
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

            result.shouldBeLeft {
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

            result.shouldBeRight {
                it.shouldBeInstanceOf<Project>()
            }
        }
    }

}