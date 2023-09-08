+++ 
title = "UserNames (Redis Transactional)"
weight = 1000 
type="docs"
+++

Here is a projection that handles _UserCreated_ and
_UserDeleted_ events. It solves the same problem as the example we've seen in [Spring transactional projections]({{<
ref "example-spring-tx.md">}}). However, this time we use Redis as our data store and [Redisson](https://github.com/redisson/redisson) as the access API.

## Configuration

The `@RedisTransactional` annotation provides various configuration options:

| Parameter Name    | Description                                                                                                      | Default Value |
| ----------------- | ---------------------------------------------------------------------------------------------------------------- | ------------- |
| `bulkSize`        | bulk size                                                                                                        | 50            |
| `timeout`         | timeout in milliseconds until a transaction is interrupted and rolled back                                       | 30000         |
| `responseTimeout` | timeout in milliseconds for Redis response. Starts to countdown when transaction has been successfully submitted | 5000          |
| `retryAttempts`   | maximum attempts to send transaction                                                                             | 5             |
| `retryInterval`   | time interval in milliseconds between retry attempts                                                             | 3000          |

## Constructing

Since we decided to use a managed projection, we extended the `AbstractRedisManagedProjection` class.
To configure the connection to Redis via Redisson, we injected `RedissonClient` in the constructor, calling the parent constructor.

```java
@ProjectionMetaData(serial = 1)
@RedisTransactional
public class UserNames extends AbstractRedisManagedProjection {

  public UserNames(RedissonClient redisson) {
    super(redisson);
  }
    ...
```

FactStreamPosition and Lock-Management are automatically taken care of by the underlying `AbstractRedisManagedProjection`.

In contrast to non-atomic projections, when applying Facts to the Redis data structure, the instance variable `userNames` cannot be used
as this would violate the transactional semantics. Instead, accessing and updating the
state is carried out on a transaction derived data-structure (`Map` here) inside the handler methods.

## Updating the projection

### Applying Events

Received events are processed inside the methods annotated with `@Handler` (the _handler methods_). To participate in
the transaction, these methods have an additional `RTransaction` parameter which represents the current transaction.

Let's have a closer look at the handler for the `UserCreated` event:

```java
@Handler
void apply(UserCreated e,RTransaction tx){
        Map<UUID, String> userNames=tx.getMap(getRedisKey());
        userNames.put(e.getAggregateId(),e.getUserName());
}
```

{{% alert title="Note"%}}
RTransaction handling is the responsibility of Factus. As developers, you must not call e.g. `commit()`
or `rollback()` yourself.
{{% /alert %}}

In the previous example, the method `getRedisKeys()` was used to retrieve the Redis key of the projection. Let's have a
closer look at this method in the next section.

## Default redisKey

The data structures provided by Redisson all require a unique identifier which is used to store them in Redis. The method `getRedisKey()` provides an
automatically generated name, assembled from the class name of the projection and the serial number configured with
the `@ProjectionMetaData`.

Additionally, an `AbstractRedisManagedProjection` or a `AbstractRedisSubscribedProjection` maintain the following keys
in Redis:

- `getRedisKey() + "_state_tracking"` - contains the UUID of the last position of the Fact stream
- `getRedisKey() + "_lock"` - shared lock that needs to be acquired to update the projection.

## Redisson API Datastructures vs. Java Collections

As seen in the above example, some Redisson data structures also implement the appropriate Java Collections interface.
For example, you can assign
a [Redisson RMap](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html)
also to a standard Java `Map`:

```java
// 1) use specific Redisson type
RMap<UUID, String> =tx.getMap(getRedisKey());

// 2) use Java Collections type
        Map<UUID, String> =tx.getMap(getRedisKey());
```

There are good reasons for either variant, `1)` and `2)`:

| Redisson specific                                                                                                                                                                                                                                                                                                           | plain Java          |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| extended functionality which e.g. reduces I/O load. (e.g. see [`RMap.fastPut(...)`](<https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html#fastPut(K,V)>) and [`RMap.fastRemove(...)`](<https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html#fastRemove(K...).>) | standard, intuitive |
| only option when using data-structures which are not available in standard Java Collections (e.g. [RedissonListMultimap](https://javadoc.io/doc/org.redisson/redisson/latest/org/redisson/RedissonListMultimap.html))                                                                                                       | easier to test      |

## Full Example

```java

@ProjectionMetaData(serial = 1)
@RedisTransactional
public class UserNames extends AbstractRedisManagedProjection {

  private final Map<UUID, String> userNames;

  public UserNames(RedissonClient redisson) {
    super(redisson);

     userNames = redisson.getMap(getRedisKey());
  }

  public List<String> getUserNames() {
    return new ArrayList<>(userNames.values());
  }

  @Handler
  void apply(UserCreated e, RTransaction tx) {
    tx.getMap(getRedisKey()).put(e.getAggregateId(), e.getUserName());
  }

  @Handler
  void apply(UserDeleted e, RTransaction tx) {
    tx.getMap(getRedisKey()).remove(e.getAggregateId());
  }
}
```

To study the full example, see

- [the UserNames projection using `@RedisTransactional`](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/proj/RedisTransactionalProjectionExample.java)
  and
- [example code using this projection](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/RedisTransactionalProjectionExampleITest.java)
