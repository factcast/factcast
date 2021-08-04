+++
draft = false
title = "Redis Batch Projection"
description = ""

creatordisplayname = "Maik Töpfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"

parent = "factus-projections"
identifier = "redis-batch-projections"
weight = 1023
+++

A *Redis batch projection* is a [transactional projection]({{< ref "transactional_projections.md">}}) 
based on [Redisson RBatch](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBatch.html)

Working with a *Redis batch projection* is **asynchronous** as multiple commands are collected and 
transmitted later at a convenient point in time:
    
*Multiple commands can be sent in a batch using RBatch object in a single network call. 
Command batches allows to reduce the overall execution time of a group of commands.* (Reddision RBatch docs) 

A *Redis batch projection* is recommended for projections which
- handle many events and
- don't require reading the current projection state in an event handler. 

For a synchronous alternative which allows access to the projection state during event handling, 
see [Redis transactional projection]({{<ref "redis-transactional-projections.md">}}).

Structure
---------

A *Redis batch projection* is defined as follows:

- it is annotated with `@RedisBatch`
- it extends either
    - the class `AbstractRedisManagedProjection` or
    - the class `AbstractRedisSubscribedProjection`
- it provides the serial number of the projection via the `@ProjectionMetaData` annotation
- the handler methods receive an additional `RBatch` parameter

This structure is similar to a [Redis transactional projection]({{<ref "redis-transactional-projections.md#structure">}}), 
only the `@RedisBatch` annotation and the `RBatch` method parameter differ.  


Example
-------

We continue using the [previously introduced example]({{<ref "redis-transactional-projections.md#example">}}) of a projection handling 
*UserCreated* and *UserDeleted* events:

```java
@ProjectionMetaData(serial = 1)
@RedisBatched
public class UserNames extends AbstractRedisManagedProjection {

    public UserNames(RedissonClient redisson) {
        super(redisson);
    }

    public List<String> getUserNames() {
        RMap<UUID, String> userNames = redisson.getMap(getRedisKey());
        return new ArrayList<>(userNames.values());
    }

    @Handler
    void apply(UserCreated created, RBatch batch) {
        RMapAsync<UUID, String> userNames = batch.getMap(getRedisKey());
        userNames.putAsync(created.getAggregateId(), created.getUserName());
    }

    @Handler
    void apply(UserDeleted deleted, RBatch batch) {
        batch.getMap(getRedisKey()).removeAsync(deleted.getAggregateId());
    }
}
```

We are using a managed projection, hence we extend the `AbstractRedisManagedProjection` class.
To let Factus take care of the batch submission and to automatically persist the Fact stream position, 
we provide the parent class with the instance of the Redission client (call to `super(...)`)


Applying Events
---------------

To access the batch, the handler methods require an additional `RBatch` parameter:

```java
@Handler
void apply(UserCreated created, RBatch batch) {
    RMapAsync<UUID, String> userNames = batch.getMap(getRedisKey());
    userNames.putAsync(created.getAggregateId(), created.getUserName());
}
```
First, we ask the batch to load the projection's state from Redis. We receive 
an [`RMapAsync`](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMapAsync.html) object 
which offers asynchronous versions of the common `Map` methods.
By calling `putAsync(...)` we add the extracted event data to the map. Underneath, the `RBatch` collects this change and, at a convenient time, transmits it together with other changes to Redis.

To obtain the Redis key of this projection, we have used the `getRedisKey()` method 
which was introduced [here]({{<ref "redis-transactional-projections.md#automatic-redis-key">}}).

Note: Due to their asynchronous nature, calling `*Async(...)` methods on the `RMapAsync` object, is always non-blocking.    



