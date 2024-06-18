+++
title = "Factcast CLI"
type="docs"
weight = 100
+++

In order to help with quick testing or debugging, FactCast provides a **very** simple CLI that you can use to publish Facts or subscribe and print Facts received to stdout.

## Usage

Once module factcast-grpc-cli is built, it provides a self-contained fc-cli.jar in its target folder. In order to use it, you can either run

```shell
java -jar path_to/fc-cli.jar <OPTIONS> <COMMAND> <COMMAND OPTIONS>
```

or just execute it as

```shell
path_to/fc-cli.jar <OPTIONS> <COMMAND> <COMMAND OPTIONS>
```

Help output at the time of writing is

```
Usage: fc-cli [options] [command] [command options]
  Options:
    --debug
      show debug-level debug messages
    --address
      the address to connect to
      Default: static://localhost:9090
    --basic, -basic
      Basic-Auth Crendentials in the form "user:password"
    --no-tls
      do NOT use TLS to connect (plaintext-communication)
    --pretty
      format JSON output
  Commands:
    catchup      Read all the matching facts up to now and exit.
      Usage: catchup [options]
        Options:
          -from
            start reading AFTER the fact with the given id
        * -ns
            the namespace filtered on

    follow      read all matching facts and keep connected while listening for
            new ones
      Usage: follow [options]
        Options:
          -from
            start reading AFTER the fact with the given id
          -fromNowOn
            read only future facts
        * -ns
            the namespace filtered on

    publish      publish a fact
      Usage: publish [options]
        Options:
        * --header, -h
            Filename of an existing json file to read the header from
        * --payload, -p
            Filename of an existing json file to read the payload from

    enumerateNamespaces      lists all namespaces in the factstore in no
            particular order
      Usage: enumerateNamespaces

    enumerateTypes      lists all types used with a namespace in no particular
            order
      Usage: enumerateTypes namespace

    serialOf      get the serial of a fact identified by id
      Usage: serialOf id

```
