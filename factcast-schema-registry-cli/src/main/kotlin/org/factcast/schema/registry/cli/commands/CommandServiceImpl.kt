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

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Path
import jakarta.inject.Singleton
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.ProjectService
import org.factcast.schema.registry.cli.registry.DistributionCreatorService
import org.factcast.schema.registry.cli.validation.ValidationService
import org.factcast.schema.registry.cli.validation.formatErrors

private val logger = KotlinLogging.logger {}

@Singleton
class CommandServiceImpl(
    private val fileSystemService: FileSystemService,
    private val validationService: ValidationService,
    private val projectService: ProjectService,
    private val distributionCreatorService: DistributionCreatorService
) : CommandService {
    override fun build(sourceRoot: Path, outputRoot: Path, whiteList: Path?, removedSchemaProps: Set<String>) = try {
        fileSystemService.deleteDirectory(outputRoot)

        logger.info("Starting building Factcast Schema Registry")
        logger.info("Input: $sourceRoot")
        logger.info("Output: $outputRoot")
        whiteList?.let { logger.info("White list: $whiteList") }
        logger.info("")

        val project = projectService.detectProject(sourceRoot, whiteList)

        validationService
            .validateProject(project)
            .fold({ errors ->
                formatErrors(errors).forEach { logger.error(it) }

                1
            }, {
                try {
                    distributionCreatorService.createDistributable(outputRoot, it, removedSchemaProps)

                    logger.info("Build finished!")

                    0
                } catch (e: IOException) {
                    logger.error(e) { "Something went wrong" }

                    1
                }
            })
    } catch (e: IllegalArgumentException) {
        logger.error(e) { "Invalid paths." }

        1
    }

    override fun validate(sourceRoot: Path, whiteList: Path?) = try {
        logger.info("Starting validating Factcast Schema Registry")
        logger.info("Input: $sourceRoot")
        whiteList?.let { logger.info("White list: $whiteList") }
        logger.info("")

        val project = projectService.detectProject(sourceRoot, whiteList)

        validationService
            .validateProject(project)
            .fold({ errors ->
                formatErrors(errors).forEach { logger.error(it) }
                logger.info("")
                logger.error("Validation failed!")

                1
            }, {
                logger.info("Project seems to be valid!")

                0
            })
    } catch (e: IllegalArgumentException) {
        logger.error(e) { "Invalid paths" }

        1
    }
}
