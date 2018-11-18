+++
draft = false
title = "Migration Guide"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "migration"
identifier = "migration"
weight = 1000
+++

## Upgrading to 0.0.30

#### Spring Boot 2

If you use Spring boot, please note, that all projects now depend on Spring Boot 2.1 artifacts. You can still use the FactCast-client in your Spring Boot 1.5 application. 

For an example see **examples/factcast-example-client-spring-boot1/**.

#### Plaintext vs TLS

There was a dependency upgrade of [grpc-spring-boot-starter](https://github.com/yidongnan/grpc-spring-boot-starter) in order to support TLS. Note that the default client configuration is now switched to TLS. That means, if you want to continue communicating in an unencrypted fashion, you need to set an application property of **'grpc.client.factstore.negotiation_type=PLAINTEXT'**. 

#### Testcontainers / Building and Testing

In order to run integration tests, that need a Postgres to run, FactCast now uses [Testcontainers](https://www.testcontainers.org/usage/database_containers.html) in order to download and run an ephemeral Postgres.
For this to work, the machine that runs test must have docker installed and the current user needs to be able to run and stop docker containers.

You can still override this behavior by supplying an Environment-Variable **'pg_url'** to use a particular postgres instead. This might be important for build agents that themselves run within docker and do not provide Docker-in-Docker. 


## Upgrading to 0.0.14

* Incompatible change in GRPC API

The GRPC API has changed to enable non-breaking changes later. (Version endpoint added)
The result is, that you have to use > 0.0.14 on Client and Server consistently.

## Noteworthy 0.0.12

* Note that the jersey impl of the REST interface has its own <a href="https://github.com/Mercateo/factcast-rest-jersey">place on github now.</a> and got new coordinates: **org.factcast:factcast-server-rest-jersey:0.0.12.** If you use the REST Server, you'll need to change your dependencies accordingly

* There is a BOM within factcast at org.factcast:factcast-bom:0.0.12 you can use to conveniently pin versions - remember that factcast-server-rest-jersey might not be available for every milestone and is not part of the BOM










