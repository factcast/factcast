+++
draft = false
title = "Spring Boot Server fat-jar"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup"
identifier = "fatjar"
weight = 100

+++

## Building

First of all, build factcast completely if not yet done by running


```sh
mvn install
```


In order to run a simple example Factcast server, you could enter the project **factcast-examples/factcast-example-server** and run

```sh
mvn spring-boot:run
```

or run it in your IDE. Note that it will use **TestContainer** to *start an ephemeral postgres instance* for you. That means, you need to have a **runnable Docker** installed on your machine. 

In case you want to use your local Postgres instead, take a look at ExampleServerWithPostgresContainer to find out how what is necessary to use a pgsql. After all, this is just a very simple Spring Boot application using JDBC.

As expected, running


```sh
mvn package
```

will create a standard spring boot fat jar ```target/factcast.jar``` that can be run instantly.

[{{<icon name="circle-arrow-right" size="small">}}Read more on Ports]({{%relref "/setup/examples/ports.md"%}})
