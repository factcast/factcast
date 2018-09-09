+++
draft = false
title = "Spring Boot fat-jar"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup_server"
identifier = "fatjar"
weight = 10

+++

## Building

In order to build a fat jar from source, enter the project **factcast-server** and run

```sh
mvn package
```


This will create a standard spring boot fat jar ```target/factcast-server.jar``` that can be run instantly.

## Usage:

To run the jar, pass the necessary configuration info as -D parameters:

```sh
java -Dspring.datasource.url=jdbc:postgresql://<POSTGRES-SERVER>/<DATABASENAME>?user=<USERNAME>&password=<PASSWORD> -Dmanagement.security.enabled=false -jar target/factcast.jar

```
[{{%icon circle-arrow-right%}}Read more on Ports]({{%relref "/setup/server/ports.md"%}})
