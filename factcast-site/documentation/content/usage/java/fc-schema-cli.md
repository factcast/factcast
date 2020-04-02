+++
draft = false
title = "Schema Registry CLI"
description = ""
date = "2017-04-24T18:36:24+02:00"
weight = 210

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"

[menu.main]
parent = "usage"
identifier = "schema-registry-cli"

+++

This CLI provides a convenient way to create a suitable Schema Registry for your Factcast installation.
It will give you the ability to validate events against examples and to make sure that there's always a upcast and if 
necessary a downcast transformation.

It produces a human and a machine readable output. You will have to use [hugo](https://gohugo.io/) in order to get a 
proper static website.

A working example can be found [here](/example-registry/). 

## Build the example

The example will be build during `mvn install`, but you can reach the same via 

`$  java -jar target/fc-schema-cli.jar build -p ../factcast-examples/factcast-example-schema-registry/src/main/resources`

`build` validates and builds the example and also produces a `output` directory that contains the static website. Inside this folder run 

`$ hugo server`

to get quick feedback or

`$ hugo`

in order to create the deployable schema registry (located at `output/public`).

## About CI Pipelines and Artifacts

We propose to the following pipeline

Build -> Package -> Upload

Build:  
- runs the fc-schema-cli to build the registry
- fails on wrong input/broken schema

Package:
- runs `$ hugo` in order to produce the artifact

Upload:
-  uploads `output/public` to static file server (like S3)

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
Tool for working with the Factcast Schema Registry spec
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  validate  Validate your current events
  build     Validates and builds your registry
```
