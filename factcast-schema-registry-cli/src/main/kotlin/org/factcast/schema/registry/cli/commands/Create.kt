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

import java.nio.file.Paths
import javax.inject.Inject
import kotlin.system.exitProcess
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "create",
    mixinStandardHelpOptions = true,
    description = ["Create an example schema registry"]
)
class Create : Runnable {
    @Option(names = ["-t", "--target-path"], description = ["The directory, into which to place the example registry. Will place a folder factcast-example-schema-registry into that directory, which contains a maven pom and an example source."])
    var targetPath: String = Paths.get(".").toString()

    @Inject
    lateinit var commandService: CommandService

    override fun run() {
        val targetPathNormalized = Paths.get(targetPath).toAbsolutePath().normalize()

        val exitCode = commandService.create(targetPathNormalized)

        if (exitCode != 0)
        exitProcess(exitCode)
    }
}
