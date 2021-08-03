+++
draft = false
title = "Redis Transactional Projection"
description = ""

creatordisplayname = "Maik Töpfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"

parent = "factus-projections"
identifier = "redis-transactional-projections"
weight = 1022
+++

A *Redis transactional projection* is a [transactional projection]({{<ref "transactional_projections">}}) 
based on [Redission RTransaction](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTransaction.html).

Compared to a [Spring transactional projection]({{< ref "spring-transactional-projections.md">}}), a *Redis transactional projection* is more lightweight since
- transactionallity is directly provided by `RTransaction`. There is no need to deal with Spring's `PlatformTransactionManager`   
- the fact stream position is automatically managed (see example below)
    
Working with a *Redis transactional projection* is **synchronous**. To ensure permanent data consistency, the Redission client 
constantly communicates with the Redis server. 

For this reason, a *Redis transactional projection* is best used for projections which
- handle only a few events and 
- which need to read the projection data during the handling of an event. 
 
For a more performant alternative see [Redis batch projection]({{<ref "redis-batch-projection.md">}})

General Structure
-----------------

A *Redis transactional projection* has the following features:
- it is annotated with `@RedisTransactional`
- it extends either 
    - the class `AbstractRedisManagedProjection` or 
    - the class `AbstractRedisSubscribedProjection`
- it provides the serial number of the projection via the `@ProjectionMetaData` annotation


Example
-------

Let's look at an example. Here is a projection which handles *UserCreated* and 
*UserDeleted* events. It solves the same problem as the example we've seen in [Spring transactional projections]({{< ref "spring-transactional-projections.md">}}).
However, this time we use Redis as our data store:   
 
```java
@ProjectionMetaData(serial = 1)
@RedisTransactional
public class UserNames extends AbstractRedisManagedProjection {

    public UserNames(RedissonClient redisson) {
        super(redisson);
    }

    public List<String> getUserNames() {
        Map<UUID, String> userNames = redisson.getMap(getRedisKey());
        return new ArrayList<>(userNames.values());
    }

    @Handler
    void apply(UserCreated e, RTransaction tx) {
        Map<UUID, String> userNames = tx.getMap(getRedisKey());
        userNames.put(e.getAggregateId(), e.getUserName());
    }

    @Handler
    void apply(UserDeleted e, RTransaction tx) {
        tx.getMap(getRedisKey()).remove(e.getAggregateId());
    }
}
```
As we decided for a [managed projection]({{< ref "managed-projection.md">}}), we extend the `AbstractRedisManagedProjection` class.
The call to `super(...)` enables Factus to take care of transaction management and to automatically persist 
the Fact stream position. 

In contrast to [other projection types]({{< ref "local-managed-projection.md">}}),
the `UserNames` projection above does not define an instance variable to store the projection's state. 
Instead,  accessing and updating the state is carried out inside the single handler methods. 
    

Applying Events 
--------------
Received events are processed inside the methods annotated with `@Handler` (the "handler methods"). To participate in the transaction, 
these methods have an additional `RTransaction` parameter which represents the current transaction.

Let' have a closer look at the handler for the `UserCreated` event:

```java
@Handler
void apply(UserCreated e, RTransaction tx) {
    Map<UUID, String> userNames = tx.getMap(getRedisKey());
    userNames.put(e.getAggregateId(), e.getUserName());
}
```

First we ask the transaction to load the projection's state from Redis. 
In this case it's a map with `UUID` keys and `String` values. 
Then we update the projection by calling `put` on the map and providing it with the user ID and the user name extracted from the event.

Note: Transaction handling is the responsibility of Factus. As developers, we must not call e.g. `commit()` or `rollback()` ourselfs. 
Instead, we leave the task of committing the transaction in right moment to Factus.

In the previous example the method `getRedisKeys()` was used to retrieve the Redis key of the projection. 
Let's have a closer look at this method in the next section.


Automatic Redis Key
--------------------
The data structures provided by the [`RTransaction`](https://javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTransaction.html)
all require a unique identifier which is used to store them in Redis. The method `getRedisKey()` provides an automatically generated name,
assembled from the class name of the projection and the serial number configured with the `@ProjectionMetaData`.
For example, for our `UserNames` projection, the generated name would be `package.of.UserNames_1`.

Additionally, a `AbstractRedisManagedProjection` or a `AbstractRedisSubscribedProjection` maintain the following keys in Redis:
- `getRedisKey() + "_state_tracking"` - contains the UUID of the last position of the Fact stream
- `getRedisKey() + "_lock"` - shared lock. Needs to be acquired to update the projection.

This diagram summarized the Redis keys of a *Redis transactional projection*:

{{<mermaid>}}
graph LR
    F[UserNames projection] -->|stores projection data in| P[ 1. ...UserNames_1]
    F -->|automatically updates Fact stream position in| S[ 2. ...UserNames_1_state_tracking]
    F -->|handles concurrent update attempts via| L[ 3. ...UserNames_1_lock]
{{</mermaid>}}
*By default a projection is related to three keys in Redis. The application programer uses the first one to access the projection data. 
The last two are managed automatically by Factus.*    


Writing The Fact Position
-------------------------
In contrast to the manual steps in the [*Spring transactional projection*]({{<ref spring-transactional-projections.md >}}),
updating the position of the Fact stream is handled automatically. From a developer perspective, no action is necessary. 


Redission Data Structures
-------------------------
As demonstrated in the example above, Redission provides implementations for standard Java data structures like `Map`. 
However, Alternatively also the specific Redission type (e.g. [RMap](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html) 
could have been used. To support your decision making here are some pros and cons: 

Pro Java Standard Interface:
- standard Java -> no reading required
- easier to test, particularly when the data structure is used for more than just `put` or `remove`    

Pro Reddision Types:
- extended functionality which e.g. reduces I/O load. (e.g. see [`RMap.fastPut(...)`](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html#fastPut(K,V)) 
and [`RMap.fastRemove(...)`](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html#fastRemove(K...).)
- data structures which are not available in standard Java Collections (e.g. [RedissonListMultimap](https://javadoc.io/doc/org.redisson/redisson/latest/org/redisson/RedissonListMultimap.html))
