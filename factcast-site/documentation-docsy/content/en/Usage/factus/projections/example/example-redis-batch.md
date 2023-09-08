+++
title = "UserNames (Redis Batched)"
weight = 1100
type="docs"
+++

We continue using the [previously introduced example]({{<ref "redis-transactional-projections.md#example">}}) of a projection handling
_UserCreated_ and _UserDeleted_ events:

## Configuration

The `@RedisBatched` annotation provides various configuration options:

| Parameter Name    | Description                                          | Default Value |
| ----------------- | ---------------------------------------------------- | ------------- |
| `bulkSize`        | bulk size                                            | 50            |
| `responseTimeout` | timeout in milliseconds for Redis response           | 5000          |
| `retryAttempts`   | maximum attempts to transmit batch of Redis commands | 5             |
| `retryInterval`   | time interval in milliseconds between retry attempts | 3000          |

---

## Constructing

Since we decided to use a managed projection, we extended the `AbstractRedisManagedProjection` class.
To configure the connection to Redis via [Redisson](https://github.com/redisson/redisson),
we injected RedissonClient in the constructor, calling the parent constructor.

```java
@ProjectionMetaData(serial = 1)
@RedisTransactional
public class UserNames extends AbstractRedisManagedProjection {

  public UserNames(RedissonClient redisson) {
    super(redisson);
  }
    ...
```

We are using a managed projection, hence we extend the `AbstractRedisManagedProjection` class.
To let Factus take care of the batch submission and to automatically persist the Fact stream position and manage the locks,
we provide the parent class with the instance of the Redisson client (call to `super(...)`)

## Updating the projection

### Applying Events

To access the batch, the handler methods require an additional `RBatch` parameter:

```java
@Handler
void apply(UserCreated created, RBatch batch) {
    batch.getMap(getRedisKey())
          .putAsync(created.getAggregateId(), created.getUserName());
}
```

We use a batch-derived
[`RMapAsync`](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMapAsync.html) object
which offers asynchronous versions of the common `Map` methods.
By calling `putAsync(...)` we add the extracted event data to the map. Underneath, the `RBatch` collects this change and,
at a convenient point in time, transmits it together with other changes to Redis.

{{% alert title="Note"%}}
RBatch handling is the responsibility of Factus. As developers, you must not call e.g. `execute()`
or `discard()` yourself.
{{% /alert %}}

{{% alert title="Note"%}}
FactStreamPosition and Lock-Management are automatically taken care of by the underlying `AbstractRedisManagedProjection`.
{{% /alert %}}

## Default redisKey

The data structures provided by Redisson all require a unique identifier which is used to store them in Redis. The method `getRedisKey()` provides an
automatically generated name, assembled from the class name of the projection and the serial number configured with
the `@ProjectionMetaData`.

Additionally, an `AbstractRedisManagedProjection` or a `AbstractRedisSubscribedProjection` maintain the following keys
in Redis:

- `getRedisKey() + "_state_tracking"` - contains the UUID of the last position of the Fact stream
- `getRedisKey() + "_lock"` - shared lock that needs to be acquired to update the projection.

## Redisson API Datastructures vs. Java Collections

As seen in the above example, some Redisson data-structures also implement the appropriate Java Collections interface.
For example, you can assign
a [Redisson RMap](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html)
also to a standard Java `Map`:

```java
// 1) use specific Redisson type
        RMap<UUID, String> = tx.getMap(getRedisKey());

// 2) use Java Collections type
        Map<UUID, String> = tx.getMap(getRedisKey());
```

There are good reasons for either variant, `1)` and `2)`:

| Redisson specific                                                                                                                                                                                                                                                                                                           | plain Java          |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| extended functionality which e.g. reduces I/O load. (e.g. see [`RMap.fastPut(...)`](<https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html#fastPut(K,V)>) and [`RMap.fastRemove(...)`](<https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RMap.html#fastRemove(K...).>) | standard, intuitive |
| only option when using data-structures which are not available in standard Java Collections (e.g. [RedissonListMultimap](https://javadoc.io/doc/org.redisson/redisson/latest/org/redisson/RedissonListMultimap.html))                                                                                                       | easier to test      |

## Full Example

```java
@ProjectionMetaData(serial = 1)
@RedisBatcheded
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

To study the full example, see

- [the UserNames projection using `@RedisBatched`](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/proj/RedisBatchedProjectionExample.java) and
- [example code using this projection](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/RedisBatchedProjectionExampleITest.java)
