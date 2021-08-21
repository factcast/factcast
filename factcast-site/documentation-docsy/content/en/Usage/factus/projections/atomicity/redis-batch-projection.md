+++
title = "Redis Batched"
weight = 1100
type="docs"
+++

A *Redis batch projection* is a [atomic projection]({{< ref "atomicity">}}) 
based on [Redisson RBatch](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBatch.html). 
Like [Redis transactional projections]({{< ref "redis-transactional-projections.md">}}), also this projection type 
is more lightweight than [Spring transactional projections]({{< ref "spring-transactional-projections.md">}}). 
No Spring `PlatformTransactionManager` is needed, 
the [RBatch](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBatch.html) object of 
the [Redission library](https://github.com/redisson/redisson) is enough.  

Working with a *Redis batch projection* is **asynchronous** as multiple commands are collected and 
executed later atomically.
    
Other than a transaction, writes registered (but not yet executed) on a Redis batch can not be read. 

A *Redis batch projection*, therefore, is recommended for projections which
- handle a lot of events and
- don't require reading the current projection state in an event handler.

*Multiple commands can be sent in a batch using RBatch object in a single network call.
Command batches allow to reduce the overall execution time of a group of commands.* (taken from Reddision RBatch docs)

For a synchronous alternative that allows access to the projection state during event handling, 
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

A *Redis batch projection* supports [managed-]({{< ref "managed-projection.md" >}}) 
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
