---
title: "Docker"
type: docs
weight: 900
description: Building a FactCast server for integration testing using docker
---

## Building

In order to build a standard docker container from source, enter the project **factcast-docker** and run

```shell
mvn dockerfile:build
```

This will create a docker container as factcast/factcast and needs nothing more than the database URL to run.

## Usage:

The docker container can be started

```shell
docker run -e"spring.datasource.url=jdbc:postgresql://<POSTGRES-SERVER>/<DATABASENAME>?user=<USERNAME>&password=<PASSWORD>" -p 9090:9090 -p 8080:8080 -p 8081:8081 factcast/factcast
```

Note, that the resulting server is optimized and supposed to be used for integration testing only.
**Do not use it in production**
