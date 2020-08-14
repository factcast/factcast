+++
draft = false
title = "Aggregates"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus-projections"
identifier = "factus-projections-snapshot-aggregates"
weight = 8

+++

![](../ph_a.png)

Another special flavor of a Snapshot Projection is an Aggregate. An Aggregate extends the notion on Snapshot Projection by bringing in an aggregate Id. This of the `UserNames` example. It does not make sense to maintain two different UserNames Projections, becuase by definition, the UserNames projection should contain **all** UserNames in the System.
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

As you can see, `find` returns the user as an Optional (being empty if there never was **any** EventObject published regarding that User), whereas fetch returns the User unwrapped and fails if there is no Fact for that user found.

All the rules from SnapshotProjections apply: The User instance is (together with its id) stored as a snapshot at the end of the operation.
