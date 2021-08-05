---
title: "Technical Choices"
menu:
main:
weight: 10
type: docs
---

Based on the aforementioned requirements, let us highlight some technical choices.

## Choice #1: PostgreSQL for persistence

Some Reasons for choosing PostgreSQL as persistence layer for the Events:

### Serializability / Atomicity

Both technical requirements are trivial when choosing a RDBMS, due to its ACID nature and the availability of Sequences.

### Familiarity

Monitoring, Alerting, Backup, Point in time recovery, Authentication / Authorization, read-replication, fail-over ... All of those are properties of a good RDBMS and it is hard to find more mature solutions than the ones we can find there.

### Flexible Querys

While Document datastores like [MongoDB](https://mongodb.com) certainly have more to offer here, PostgreSQL is surprisingly good with JSON. FactCast uses **GIN** Indexes on **JSONB** Columns in order to find matching Facts for subscriptions easily.

### Coordination

With ```LISTEN``` and ```NOTIFY``` PostgreSQL makes the transition from a passive Data-store to a reactive one, that can be used to guarantee low latency pushes of new Facts down to subscribers, irrelevant at which instance of FactCast the write has happened, without the need of any further message-bus/topic/whatever.

### Read-Replicas

A solved problem, that might help you with *more than moderate* traffic. While we would rather consider *partitioning your Facts*, in the first place, it might be a welcome 'last resort' when you have lots and lots of subscribers. 

### Cloud-ready

With AWS RDS for instance, it is rather trivial to setup and operate a PostgreSQL that satisfies the above needs. It is unlikely to find a respectable cloud platform without postgresql.   

## Choice #2: GRPC


When it comes to raw performance, REST might not always be the best option. In order to offer a more compact transport, but yet stay platform neutral, FactCast also has a GRPC API. 
GRPC has a lot of implementations in languages like: 

* C++
* Java
* Python
* Go
* Ruby
* C#
* Node.js
* Android Java
* Objective C
* and even PHP

[{{%icon circle-arrow-right%}}GRPC.io ](http://www.grpc.io/)

## Choice #3: Spring Boot (Server)

[Spring Boot](https://projects.spring.io/spring-boot/) is a simple Framework to quickly spin up Java Servers. The FactCast Server is implemented using Spring Boot as a container.

## Choice #4: Spring (GRPC Client)

In order to make it easy to use the GRPC Client from java, the factcast-client-grpc module depends on Spring as well. This dependency is not exactly tight, so if there is a good reason to, you might want to implement a GRPC CLient free of Spring dependencies. If so, let us know.



