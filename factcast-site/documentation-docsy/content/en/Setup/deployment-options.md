---
title: "Deployment Options"
type: docs
weight: 1000
description: Options for deploying the FactCast server.
---

The FactCast Server can be deployed in different ways:

### Single node deployment (start here)

This is the simplest way of deployment. You create a simple Java project where your configuration
lives, include the necessary dependencies and deploy it as the single FactCast Server with exclusive access
to the factstore database.

### Multi node deployment

As the FactCast Server does not contain state that lives outside the database, or
this state is updated instantly triggered by database notifications, you can achieve
horizontal scalability by adding nodes as you see fit. Depending on your setup you might decide for client
side load-balancing as provided by the GRPC library, or use a dedicated load-balancer.
All nodes in this scenario need full read-write access to the factstore database.

### Extra read-only instance

As discussed in [UI deployment options](../UI/Setup/deployment-options.md), you might come across the wish to have
instances, that - while still having read-write access to the database - disallow any write.
This is achieved by setting `factcast.store.readOnlyModeEnabled=true`

### Extra read-only instance based on read replica

In order to completely segregate the use of a read-only instance from the production system, it might
also make sense to not attach it to the main postgres instance, but to a read-replica instead.
This ensures that load created by clients of this instance cannot interfere with the production system.

Due to the way the postgres based FactStore communicates with the database (LISTEN), this cannot be achieved by
only setting the `factcast.store.readOnlyModeEnabled=true`, because `LISTEN` is not allowed on read replicas.

In order to substitute for this mechanism, there is an alternative channel where those notifications can be
communicated. One node takes the role of the **emitter**, the instance(s) based on the
replica have the role of **subscriber** in regard to those notifications.

At the time of writing, the codebase only has Redis PUB/SUB implemented, but this can easily be extended
to any kind of messaging system.

Given, you have a redis instance nearby, what you'd need to do is to choose **one** instance with read-write
access to the primary database to additionally **emit** the notificatiions, and your replica based
FactCast Server to consumer those.

#### Emitter

On the read-write instance you would add the necessary dependencies and configure the URL to redis:

pom.xml:

```xml

<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-store-pub-redis</artifactId>
</dependency>
```

application.yml:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_ADDRESS}
      port: ${REDIS_PORT}
      # and maybe
      ssl:
        enabled: true
```

#### Subscriber

The read-only side needs to be configured accordingly:

pom.xml:

```xml

<dependency>
    <groupId>org.factcast</groupId>
    <artifactId>factcast-store-sub-redis</artifactId>
</dependency>
```

application.yml:

```yaml
spring:
  datasource:
    url: ${REPLICA_URL}
    username: ${REPLICA_USER}
    password: ${REPLICA_PASSWORD}
  data:
    redis:
      host: ${REDIS_ADDRESS}
      port: ${REDIS_PORT}
      # and maybe
      ssl:
        enabled: true

factcast:
  store:
    readOnlyModeEnabled: true
```
