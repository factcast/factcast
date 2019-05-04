+++
draft = true
title = "Docker"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup"
identifier = "docker"
weight = 20

+++

### DEPRECATED Documentation. Will be updated https://github.com/Mercateo/factcast/issues/312

## Building

In order to build a standard docker container from source, enter the project **factcast-server** and run

```sh
mvn docker:build
```

This will create a docker container as factcast/factcast-server that includes REST and GRPC adapters and needs nothing more than the database URL to run.

## Usage:

The docker container can be started

```
docker run -e"spring.datasource.url=jdbc:postgresql://<POSTGRES-SERVER>/<DATABASENAME>?user=<USERNAME>&password=<PASSWORD>" -p 9090:9090 -p 8080:8080 -p 8081:8081 factcast/factcast-server
```
[{{<icon name="circle-arrow-right" size="small">}}Read more on Ports]({{%relref "/setup/server/ports.md"%}})
