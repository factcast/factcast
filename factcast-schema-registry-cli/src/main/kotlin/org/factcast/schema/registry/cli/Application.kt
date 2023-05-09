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
package org.factcast.schema.registry.cli

import io.micronaut.configuration.picocli.PicocliRunner
import io.micronaut.core.annotation.Introspected
import org.factcast.schema.registry.cli.commands.Build
import org.factcast.schema.registry.cli.commands.Validate
import org.factcast.schema.registry.cli.utils.BANNER
import picocli.CommandLine
import picocli.CommandLine.Command
import kotlin.system.exitProcess

@Command(
    name = "fc-schema",
    header = [BANNER],
    description = ["Tool for working with the FactCast Schema Registry spec"],
    subcommands = [Validate::class, Build::class],
    mixinStandardHelpOptions = true
)
@Introspected
class Application : Runnable {
    override fun run() {
        CommandLine.usage(this, System.out)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = PicocliRunner.execute(Application::class.java, *args)
            if (exitCode != 0)exitProcess(exitCode)
        }
    }
}
