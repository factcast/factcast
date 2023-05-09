---

title: "Design"

weight: 10
type: docs
----------

## Requirements

#### Some of the requirements that lead to the current design of FactStore are

- minimal operational effort needed
- Stateless for simple Fail-over
- Stateless for horizontal scalability (for reading)
- well-known data persistence layer for ease of operation
- well-known data persistence layer to be future proof in the light of the german data protection laws (and yes, that's not a trivial one)
- fast _enough_ when writing
- fast for reading
- simple (!) enough for teams with very different tools to be able to integrate with their chosen environment

##### NON-Requirements are

- excessive write performance (as in high speed trading)
- full-blown Application Framework

## Choices

Based on the aforementioned requirements, let us highlight some technical choices.

### PostgreSQL for persistence

Some Reasons for choosing PostgreSQL as persistence layer for the Events:

#### Serializability / Atomicity

Both technical requirements are trivial when choosing a RDBMS, due to its ACID nature and the availability of Sequences.

#### Familiarity

Monitoring, Alerting, Backup, Point in time recovery, Authentication / Authorization, read-replication, fail-over ... All of those are properties of a good RDBMS and it is hard to find more mature solutions than the ones we can find there.

#### Flexible Querys

While Document datastores like [MongoDB](https://mongodb.com) certainly have more to offer here, PostgreSQL is surprisingly good with JSON. FactCast uses **GIN** Indexes on **JSONB** Columns in order to find matching Facts for subscriptions easily.

#### Coordination

With `LISTEN` and `NOTIFY` PostgreSQL makes the transition from a passive Data-store to a reactive one, that can be used to guarantee low latency pushes of new Facts down to subscribers, irrelevant at which instance of FactCast the write has happened, without the need of any further message-bus/topic/whatever.

#### Read-Replicas

A solved problem, that might help you with _more than moderate_ traffic. While we would rather consider _partitioning your Facts_, in the first place, it might be a welcome 'last resort' when you have lots and lots of subscribers.

#### Cloud-ready

With AWS RDS for instance, it is rather trivial to setup and operate a PostgreSQL that satisfies the above needs. It is unlikely to find a respectable cloud platform without postgresql.

### GRPC

When it comes to raw performance, REST might not always be the best option. In order to offer a more compact transport, but yet stay platform neutral, FactCast also has a GRPC API.
GRPC has a lot of implementations in languages like:

- C++
- Java
- Python
- Go
- Ruby
- C#
- Node.js
- Android Java
- Objective C
- and even PHP

[GRPC.io](http://www.grpc.io/)

### Spring Boot (Server)

[Spring Boot](https://projects.spring.io/spring-boot/) is a simple Framework to quickly spin up Java Servers. The FactCast Server is implemented using Spring Boot as a container.

### Spring (GRPC Client)

In order to make it easy to use the GRPC Client from java, the factcast-client-grpc module depends on Spring as well. This dependency is not exactly tight, so if there is a good reason to, you might want to implement a GRPC CLient free of Spring dependencies. If so, let us know.

## Limitations

### Multi-region distribution with Primary/Primary replication

FactCast is written (and works out of the box) for deployments that contain any number of FactCast Servers that <b>share
one PostgreSQL database</b>. To be clear: the FactCast Server itself is horizontally scalable and capable of load-balancing and failover.

While this might sound like a major limitation, it actually hardly is due to several performance
optimizations within the FactCast server, as well as the extensive scalability and failover options of todays cloud
offerings.

While there is no technical reason you cannot use FactCast in a multi-database scenario, you'll have to give it a little thought.
If you have doubts, feel free to contact us to discuss your situation.

### High-speed trading or gambling

We easily **publish thousands** and **serve and process hundreds of thousands** of facts a second. However, if your requirements are in a
completely different ballpark, you might want to look at different solutions.
