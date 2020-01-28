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
import org.factcast.schema.registry.cli.fs.FileSystemService
import org.factcast.schema.registry.cli.project.ProjectService
import org.factcast.schema.registry.cli.registry.DistributionCreatorService
import org.factcast.schema.registry.cli.validation.ValidationService
import org.factcast.schema.registry.cli.validation.formatErrors
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

@Command(name = "build", helpCommand = true, description = ["Validates and builds your registry"])
class Build : Runnable {
    @Option(names = ["-p", "--base-path"], description = ["The directory where your source files live"])
    var basePath: String = Paths.get(".").toString()

    @Option(names = ["-o", "--output"], description = ["The directory where your source files live"])
    var outputPath: String = Paths.get(".", "output").toString()

    @Inject
    lateinit var projectService: ProjectService

    @Inject
    lateinit var validationService: ValidationService

    @Inject
    lateinit var distributionCreatorService: DistributionCreatorService

    @Inject
    lateinit var fileSystemService: FileSystemService

    override fun run() {
        val tmp = fileSystemService.createTempDirectory("fc-schema")

        val outputRoot = Paths.get(outputPath).toAbsolutePath().normalize()
        val sourceRoot = Paths.get(basePath).toAbsolutePath().normalize()

        fileSystemService.deleteDirectory(outputRoot)

        logger.info("Starting building Factcast Schema Registry")
        logger.info("Input: $sourceRoot")
        logger.info("Output: $outputRoot")
        logger.info("")

        try {
            val project = projectService.detectProject(sourceRoot)

            validationService.validateProject(project).fold<Unit>({ errors ->
                formatErrors(errors).forEach { logger.error(it) }
                exitProcess(1)
            }, {
                try {
                    distributionCreatorService.createDistributable(tmp, it)

                    Files.move(tmp, outputRoot)
                    logger.info("Build finished!")
                } catch (e: Exception) {
                    logger.error(e) { "Something went wrong" }
                }
            })
        } catch (e: Exception) {
        }
    }
}
