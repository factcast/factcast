+++
title = "Redis Batched"
weight = 1100
type="docs"
+++

A _Redis batch projection_ is a [atomic projection]({{< ref "atomicity">}})
based on [Redisson RBatch](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBatch.html).
Like [Redis transactional projections]({{< ref "redis-transactional-projections.md">}}), also this projection type
is more lightweight than [Spring transactional projections]({{< ref "spring-transactional-projections.md">}}).
No Spring `PlatformTransactionManager` is needed,
the [RBatch](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBatch.html) object of
the [Redisson library](https://github.com/redisson/redisson) is enough.

Working with a _Redis batch projection_ is **asynchronous** as multiple commands are collected and
executed later in an atomic way (all or none).

## Motivation

You would want to use Redis Batched for two reasons:

- atomicity of factStreamPosition updates and your projection state updates
- increased fact processing throughput

The performance bit is achieved by skipping unnecessary factStreamPosition updates and (more importantly) by reducing the number of operations on your Redis backend by using a `redisson batch` instead of single writes for `bulkSize` updates.
The `bulkSize` is configurable per projection via the `@RedisBatched` annotation.

Redisson batches improve performance significantly by collecting operations and executing them together as well as
delegating all possible work to an async thread. It has the effect, that you cannot read non-executed (batched) changes.
If this is unacceptable for your projection, consider [Redis transactional projections]({{<ref "redis-transactional-projections.md">}}).
See the Redisson documentation for details.

Other than a transaction, writes registered (but not yet executed) on a Redis batch can not be read.

A _Redis batch projection_, therefore, is recommended for projections which

- handle a lot of events and
- don't require reading the current projection state in an event handler.

For an alternative that allows access to the projection state during event handling,
see [Redis transactional projection]({{<ref "redis-transactional-projections.md">}}).

## Configuration

In order to make use of redisson RBatch support, the necessary dependency has to be included in your project:

```xml
    <dependency>
        <groupId>org.factcast</groupId>
        <artifactId>factcast-factus-redis</artifactId>
    </dependency>

```

## Structure

A _Redis batch projection_ supports [managed-]({{< ref "managed-projection.md" >}})
or [subscribed]({{< ref "subscribed-projection.md" >}}) projections and is defined as follows:

- it is annotated with `@RedisBatched`
- it implements `RedisProjection` revealing the `RedisClient` used
- it provides the serial number of the projection via the `@ProjectionMetaData` annotation
- the handler methods receive an additional `RBatch` parameter

This structure is similar to a [Redis transactional projection]({{<ref "redis-transactional-projections.md#structure">}}),
only the `@RedisBatched` annotation and the `RBatch` method parameter differ.

## Example

```java
@Handler
void apply(SomethingHappened fact, RBatch batch) {
    myMap = batch.getMap( ... ).putAsync( fact.getKey() , fact.getValue() );
}
```

a full example can be found [here]({{< ref "example-redis-batch.md" >}})
