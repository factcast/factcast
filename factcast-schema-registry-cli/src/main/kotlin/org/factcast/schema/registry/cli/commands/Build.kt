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

import jakarta.inject.Inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Paths
import kotlin.system.exitProcess

@Command(
    name = "build",
    mixinStandardHelpOptions = true,
    description = ["Validates and builds your registry"]
)
class Build : Runnable {
    @Option(names = ["-p", "--base-path"], description = ["The directory where your source files live"])
    var basePath: String = Paths.get(".").toString()

    @Option(names = ["-o", "--output"], description = ["Output directory of the registry"])
    var outputPath: String = Paths.get(".", "output").toString()

    @Option(names = ["-w", "--white-list"], description = ["Path to an optional whitelist file."])
    var whiteList: String? = null

    @Option(
        names = ["-s", "--schema-strip-titles"],
        description = ["Remove the 'title' attribute from JSON schema files. Optional."]
    )
    var schemaStripTitles: Boolean = false

    @Option(
        names = ["--schema-remove-fields"],
        description = ["Remove arbitrary fields from the schema like title, description, example."],
        split = ","
    )
    var removeSchemaFields: Set<String> = emptySet()

    @Inject
    lateinit var commandService: CommandService

    override fun run() {
        val sourceRoot = Paths.get(basePath).toAbsolutePath().normalize()
        val outputRoot = Paths.get(outputPath).toAbsolutePath().normalize()
        val whiteListPath = whiteList?.let { Paths.get(it).toAbsolutePath().normalize() }

        val exitCode = commandService.build(
            sourceRoot,
            outputRoot,
            whiteListPath,
            if (schemaStripTitles) removeSchemaFields.plus("title") else removeSchemaFields
        )

        if (exitCode != 0)
            exitProcess(exitCode)
    }
}
