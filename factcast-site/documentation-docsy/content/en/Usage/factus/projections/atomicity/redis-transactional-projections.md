+++
title = "Redis Transactional"
weight = 1000
type="docs"
+++

A _Redis transactional projection_ is a [transactional projection]({{<ref "atomicity">}})
based on [Redisson RTransaction](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTransaction.html).

Compared to a [Spring transactional projection]({{< ref "spring-transactional-projections.md">}}), a _Redis transactional projection_ is more lightweight since

- transactionality is directly provided by `RTransaction`. There is no need to deal with Spring's `PlatformTransactionManager`
- the fact stream position is automatically managed (see example below)

## Motivation

You would want to use Redis Transactional for two reasons:

- atomicity of factStreamPosition updates and your projection state updates
- increased fact processing throughput

The performance bit is achieved by skipping unnecessary factStreamPosition updates and (more importantly) by
reducing the number of operations on your Redis backend by using `bulkSize` updates with one `redisson transsaction` instead of single writes.
The `bulkSize` is configurable per projection via the `@RedisTransactional` annotation.

Working with a _Redis transactional projection_ you can read your own uncommitted write. For this reason, a _Redis transactional projection_ is best used for projections which
need to access the projection's data during the handling of an event.

If this is not necessary, you could also use a better performing alternative: [Redis batch projection]({{<ref "redis-batch-projection.md">}})

## Configuration

In order to make use of redisson RTransaction support, the necessary dependency has to be included in your project:

```xml
    <dependency>
        <groupId>org.factcast</groupId>
        <artifactId>factcast-factus-redis</artifactId>
    </dependency>

```

## Structure

A _Redis transactional projection_ can be a [managed-]({{< ref "managed-projection.md" >}}) or
a [subscribed]({{< ref "subscribed-projection.md" >}}) projection and is defined as follows:

- it is annotated with `@RedisTransactional`
- it implements `RedisProjection` revealing the `RedisClient` used
- it provides the serial number of the projection via the `@ProjectionMetaData` annotation
- the handler methods receive an additional `RTransaction` parameter

{{% alert  title="Note" %}}

Factus provides convenient abstract classes for managed and subscribed projections:

- `AbstractRedisManagedProjection`
- `AbstractRedisSubscribedProjection`

{{% / alert %}}

## Example

```java
@Handler
void apply(SomethingHappened fact, RTransaction tx) {
    myMap = tx.getMap( ... ).put( fact.getKey() , fact.getValue() );
}
```

a full example can be found [here]({{< ref "example-redis-tx.md" >}})
