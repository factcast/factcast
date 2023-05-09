---

title: "fat-jar"
type: docs
weight: 110
-----------

## Building

First of all, build factcast completely if not yet done by running

```shell
mvn install
```

In order to run a simple example FactCast server, you could enter the project **factcast-examples/factcast-example-server** and run

```shell
mvn spring-boot:run
```

or run it in your IDE. Note that it will use **TestContainer** to _start an ephemeral postgres instance_ for you. That means, you need to have a **runnable Docker** installed on your machine.

In case you want to use your local Postgres instead, take a look at ExampleServerWithPostgresContainer to find out how what is necessary to use a pgsql. After all, this is just a very simple Spring Boot application using JDBC.

As expected, running

```shell
mvn package
```

will create a standard spring boot fat jar `target/factcast.jar` that can be run instantly.

[{{<icon name="circle-arrow-right" size="small">}}Read more on Ports]({{%relref "/Setup/ports.md"%}})
