+++
title = "Aggregate"
type = "docs"
weight = 200
+++

![](../ph_a.png#center)

Another special flavor of a Snapshot Projection is an Aggregate. An Aggregate extends the notion on Snapshot Projection
by bringing in an aggregate Id. This is the one of the `UserNames` example. It does not make sense to maintain two
different UserNames Projections, because by definition, the UserNames projection should contain **all** UserNames in the
system.
When you think of `User` however, you have different users in the System that differ in Id and (probably) UserName.
So calling `factus.fetch(User.class)` would not make any sense. Here Factus offers two different methods for access:

```java
/**
 * Same as fetching on a snapshot projection, but limited to one
 * aggregateId. If no fact was found, Optional.empty will be returned
 */
@NonNull
<A extends Aggregate> Optional<A> find(
        @NonNull Class<A> aggregateClass,
        @NonNull UUID aggregateId);

/**
 * shortcut to find, but returns the aggregate unwrapped. throws
 * {@link IllegalStateException} if the aggregate does not exist yet.
 */
@NonNull
default <A extends Aggregate> A fetch(
        @NonNull Class<A> aggregateClass,
        @NonNull UUID aggregateId) {
    return find(aggregateClass, aggregateId)
            .orElseThrow(() -> new IllegalStateException("Aggregate of type " + aggregateClass
                    .getSimpleName() + " for id " + aggregateId + " does not exist."));
}

```

As you can see, `find` returns the user as an Optional (being empty if there never was **any** EventObject published
regarding that User), whereas `fetch` returns the User unwrapped and fails if there is no Fact for that user found.

All the rules from SnapshotProjections apply: The User instance is (together with its id) stored as a snapshot at the
end of the operation. You also have the beforeSnapshot() and afterRestore() in case you want to hook into the
lifecycle (see SnapshotProjection)

---

## Aggregate Caching

Most of the time, especially when needing a consistent view of the system,
you will want find/fetch aggregates as explained before. While every call to find/fetch will create you a fresh instance
of the aggregate, it will be restored from a snapshot, so that it should be reasonably fast. What we need to remember is
that both calls will need to execute the following steps in order to get a fully consistent view of the aggregate:

- talk to the snapshotcache to get the aggregate snapshot to restore from
- deserialize it into the aggregate instance (if found)
- query the factstore for fact that happened regarding this aggregate, since the snapshot was taken

Even if all these steps are carefully tuned, there is network and lots of message passing involved, so that it will take
some time.

In high throughput situations, you might also realize that you don't need full consistency on a particular aggregate and
would be fine with a change on that aggregate arriving with some latency. This is where aggregate caches can help.

An aggregate cache is a local cache of aggregate instances that Factus will maintain for you.
When you request an aggregate via find/fetch, Factus will first check the cache if there is a "fresh enough" instance of
the aggregate available.
If so, it will return that instance instead of going to the factstore to restore a fresh instance.
The point of this cache is, that it subscribes to all facts that could potentially change the state of an aggregate of
its type, and will invalidate the respective cache entries if any of those facts arrive.

In essence, when using find/fetch with a typed aggregate cache, you will get a (potentially) slightly stale instance of
the aggregate, but you will avoid the latency added by the 3 steps mentioned above.

To enable aggregate caching, you need to register a typed cache for your aggregate type:

```java

@Bean
AggregateCache<User> getUserAggregateCache(Factus factus, FactSpecProvider fsp) {
    return new AbstractAggregateCache<User>(factus, fsp) {
    };
}
```

and then instead of calling `factus.fetch(User.class, id)`, you can call `factus.fetch(userAggregateCache, id)` if you
are ok with a stale aggregate.
Same goes for `find`.

You can configure your aggregate cache by overriding the `configure` method.
