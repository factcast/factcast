package org.factcast.schema.registry.cli.commands;

import org.factcast.schema.registry.cli.Application;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class Runner implements CommandLineRunner, ExitCodeGenerator {

  private Application command;
  private CommandLine.IFactory factory; // auto-configured to inject PicocliSpringFactory

  private int exitCode;

  public Runner(Application command, CommandLine.IFactory factory) {
    this.command = command;
    this.factory = factory;
    System.out.println("oink runner");
  }

  @Override
  public void run(String... args) throws Exception {
    System.out.println("running " + args);
    exitCode = new CommandLine(command, factory).execute(args);
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
