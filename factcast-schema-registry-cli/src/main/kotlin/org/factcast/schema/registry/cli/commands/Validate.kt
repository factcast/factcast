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
package org.factcast.schema.registry.cli.commands

import mu.KotlinLogging
import org.factcast.schema.registry.cli.project.ProjectService
import org.factcast.schema.registry.cli.validation.ValidationService
import org.factcast.schema.registry.cli.validation.formatErrors
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

@Command(name = "validate", helpCommand = true, description = ["Validate your current events"])
class Validate : Runnable {
    @Option(names = ["-p", "--base-path"], description = ["The directory where your source files live"])
    var basePath: String = Paths.get(".").toString()

    @Inject
    lateinit var projectService: ProjectService

    @Inject
    lateinit var validationService: ValidationService

    override fun run() {
        try {
            val sourceRoot = Paths.get(basePath).toAbsolutePath().normalize()

            logger.info("Starting validating Factcast Schema Registry")
            logger.info("Input: $sourceRoot")
            logger.info("")

            val project = projectService.detectProject(sourceRoot)

            validationService
                .validateProject(project)
                .fold({ errors ->
                    formatErrors(errors).forEach { logger.error(it) }
                    logger.info("")
                    logger.error("Validation failed!")
                    exitProcess(1)
                }, {
                    logger.info("Project seems to be valid!")
                })
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Invalid paths" }
            exitProcess(1)
        }
    }
}