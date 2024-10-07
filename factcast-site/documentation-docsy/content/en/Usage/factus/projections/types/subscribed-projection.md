+++
title = "Subscribed"
weight = 400
type = "docs"

+++

![](../ph_s.png#center)

The `SnapshotProjection` and `ManagedProjection` have one thing in common:
The application actively controls the frequency and time of updates by actively calling a method. While this gives the
user a maximum of control, it also requires synchronicity. Especially when building query models, this is not
necessarily a good thing. This is where the `SubscribedProjection` comes into play.

## Definition

A `SubscribedProjection` is subscribed once to a Fact-stream and is asynchronously updated as soon as the application
receives relevant facts.

Subscribed projections are created by the application and subscribed (once) to factus. As soon as Factus receives
matching Facts from the FactCast Server, it updates the projection. The expected latency is obviously dependent on a
variety of parameters, but under normal circumstances it is expected to be <100ms, sometimes <10ms.

However, its strength (being updated in the background) is also its weakness: the application never knows what state the
projection is in (_eventual consistency_).

While this is a perfect projection type for occasionally connected operations or public query models, the inherent
eventual consistency might be confusing to users, for instance in a _read-after-write_ scenario, where the user
does not see his own write. This can lead to suboptimal UX und thus should be used cautiously after carefully
considering the trade-offs.

A `SubscribedProjection` is also `StateAware` and `WriterTokenAware`. However, the token will not be released as frequently
as with a `ManagedProjection`. This may lead to "starving" models, if the process keeping the lock is non-responsive.

Please keep that in mind when implementing the locking facility.

## Read-After-Write Consistency

Factus updates subscribed projections automatically in the background. Therefore a manual update with
``
`factus.update(projection)` is not possible. In some cases however it might still be necessary to make sure a subscribed
projection has processed a fact before continuing.

One such use-case might be read-after-write consistency. Imagine a projection powering a table shown to a user. This
table shows information collected from facts `A` and `B`, where `B` gets published by the current application, but
`A` is published by another service, which means we need to use a subscribed projection. With the push of a button a user can publish a new `B` fact, creating another row
in the table. If your frontend then immediately reloads the table, it might not yet show the new row, as the subscribed
projection has not yet processed the new fact.

In this case you can use the `factus.waitFor` method to wait until the projection has consumed a certain fact. This
method will block until the fact is either processed or the timeout is exceeded.

```java
// publish a fact we need to wait on and extract its ID
final var factId = factus.publish(new BFact(), Fact::id);

factus.waitFor(subscribedProjection, factId, Duration.ofSeconds(5));
```

With this, the waiting thread will block for up to 5 seconds or until the projection has processed the fact stream up to or beyond the specified fact.
If you use this, make sure that the projection you are waiting for will actually process the fact you are waiting on.
Otherwise a timeout is basically guaranteed, as the fact will never be processed by this projection.
