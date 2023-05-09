---

title: "gRPC Client"
type: docs
weight: 160
-----------

## GRPC Clients

In order to talk to a - not in process - factstore (which is the usual setup for non-test applications), GRPC is the communication protocol used.

## Using FactCast client in Spring boot via GRPC

If you use Spring take the easy path in your Spring Boot Application by adding the appropriate dependencies to your application:

```xml
<dependency>
  <groupId>org.factcast</groupId>
  <artifactId>factcast-client-grpc</artifactId>
</dependency>
<dependency>
  <groupId>org.factcast</groupId>
  <artifactId>factcast-spring-boot-autoconfigure</artifactId>
</dependency>
```

There are example projects: **factcast-examples/factcast-example-client-spring-boot2** and **factcast-examples/factcast-example-client-spring-boot1** respectivly, that you can use as a template.

Note that factcast-client-grpc is built on top of (https://github.com/yidongnan/grpc-spring-boot-starter). If you are looking for the basic configuration properties, that is where you can find the latest version.

At the time of writing, the most relevant are:

| Name                                    | Example Value           | required |
|:----------------------------------------|:------------------------|:---------|
| grpc.client.factstore.address           | static://localhost:9090 | yes      |
| grpc.client.factstore.negotiationType   | PLAINTEXT               | no       |
| grpc.client.factstore.enable-keep-alive | true                    | no       |

