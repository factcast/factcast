+++
title = "Schema Registry CLI"
weight = 110

type="docs"
+++

This CLI provides a convenient way to create a suitable Schema Registry for your FactCast installation.
It will give you the ability to validate events against examples and to make sure that there's always an upcast and if
necessary a downcast transformation.

It produces a machine-readable output required by FactCast.

## Build the example

The example will be built during `mvn install`, but you can reach the same via

`$  java -jar target/fc-schema-cli.jar build -p ../factcast-examples/factcast-example-schema-registry/src/main/resources`

`build` validates and builds the example.

## About CI Pipelines and Artifacts

Ensure to execute the build step within your CI pipeline to fail on wrong input/broken schema and make the generated
files available to FactCast.

## Available commands and options

```
$ java -jar target/fc-schema-cli.jar -h

███████╗ █████╗  ██████╗████████╗ ██████╗ █████╗ ███████╗████████╗
██╔════╝██╔══██╗██╔════╝╚══██╔══╝██╔════╝██╔══██╗██╔════╝╚══██╔══╝
█████╗  ███████║██║        ██║   ██║     ███████║███████╗   ██║
██╔══╝  ██╔══██║██║        ██║   ██║     ██╔══██║╚════██║   ██║
██║     ██║  ██║╚██████╗   ██║   ╚██████╗██║  ██║███████║   ██║
╚═╝     ╚═╝  ╚═╝ ╚═════╝   ╚═╝    ╚═════╝╚═╝  ╚═╝╚══════╝   ╚═╝

Usage: fc-schema [-hV] [COMMAND]
Tool for working with the FactCast Schema Registry spec
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  validate  Validate your current events
  build     Validates and builds your registry
```
