package org.factcast.schema.registry.cli.commands

import org.factcast.schema.registry.cli.Application
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component
import picocli.CommandLine
import picocli.CommandLine.IFactory

@Component
class Runner(
    private val command: Application, // auto-configured to inject PicocliSpringFactory
    private val factory: IFactory
) : CommandLineRunner,
    ExitCodeGenerator {
    private var exitCode: Int = 0

    @Throws(Exception::class)
    override fun run(vararg args: String) {
        this.exitCode = CommandLine(command, factory).execute(*args)
    }

    override fun getExitCode(): Int = this.exitCode

}
