---
title: "Hitchhiker's Guide To Read-after-Write consistency"
weight: 100
type: docs
---

{{% alert title="Preface" %}}

This guide is targeting an audience that is already informed about the projection types offered by Factus, and about
their concepts.

We suggest to check out the "Projections" section of the [Factus API docs]({{< ref "/usage/factus/projections">}})
before delving into this guide.

{{% / alert %}}

## Problem statement

Factus and its projections follow the CQRS Pattern, which - next to plenty of other advantages - provides
the application with a good way to make use of _Eventual Consistency_.
Depending on the Projection you use, it can be updated

- on request (synchronous to the User on the read path)
- manually at you convenience, or
- asynchronously, pushed by the factcast server

## Eventual consistency, and when to use it

Eventual consistency is a great way of drastically improving scalability of your application in scenarios,
where the exact moment of an update of the model is not important.

Consider building a model that provides the number of currently logged-in users for your social network application.
It might be used on every web-page (in the header) and also will be publicly available, so that you really would
not want each request to go to the factcast server and find out, if the count needs to be updated.
Apart from that being a significant scalability (maybe DoS) risk, it really is not important for that number
to be exact. If it changes with a (undefined, from single digit ms up to whatever you choose) latency when
somebody logs in, nobody will care.

This latency, from the moment when a potentially interesting fact is published up to when a callback on the handler
method of a projection is invoked, we call the **window of inconsistency**.

#### Snapshot / Aggregate

The above argumentation probably gives away, that you would not want to use a SnapshotProjection in this case,
as it would probably be

- re-fetched from a SnapshotCache
- deserialized (if snapshot was found)
- updated with (often 0) facts that happened since the last time we looked
- serialized (in case new facts were applied)

before it can be queried to return a simple `int` (or `long` if you are up to something great).

What the more applicable alternative is (Managed or Subscribed Projection), depends
on your tolerance for the window of inconsistency and the level of control you'd like to have over
updating your model.

##### Managed Projection

A managed projection is called managed because the application can decide how often & when to update it.
For instance, you could decide that a window of inconsistency can be as large as 60 seconds, so that ideally
you pull the latest state every 60 seconds. You may use your favorite scheduler mechanism to call
`factus.update(myManagedUserCount);` with that fixed interval. This way you would end up with an average
latency of 30s.

This would also be slightly more efficient than using a subscribed projection if you expect _many_
relevant facts to be published within this interval, but less efficient if there was nothing
published in between.

##### Subscribed Projection

A subscribed projection will have its handler method invoked, when a relevant fact arrived.
This method is more convenient (as there is less on the application layer to do) and also
reduces the window of inconsistency to few milliseconds, depending on your networking & hardware setup.

## Eventual consistency, and when to **not** use it

The use of eventual consistent models of course needs to be limited to the read-path of you application.
When using eventual consistent models to validate constraints, you risk wrong business decisions due to race conditions.

**You have been warned.**

But sometimes, even on the read side things are not as simple as in the above example. Let's consider a backoffice
application where your customers are listed, maybe a master-detail-dialog.
As you have a lot of customers, and a window of inconsistency is _usually_ irrelevant, you may go for a
managed or subscribed projection.
There is however a use case, where inconsistency might not be appreciated:

Your customer service agent is on the phone and is asked to change the name of the customer due to marriage.
Lets assume the list-view of customers looks like:

| customerId | Name           |
| ---------- | -------------- |
| 1          | John Lennon    |
| 2          | Paul McCartney |

Clicking on "John Lennon" leads to the detail dialog where he/she changed the name, saves and gets back to this list.
If you think about it, we have a race condition here between the user again reading the model and the fact
with the updated name arrives, so that the customer service agent coud either see the old or the new name.
This is confusing people and tricking them into thinking they did something wrong, so that they try repeating.

Now you could argue that you can train your customer service agent, but what if a similar pattern happens
in the self service section of your application?

If we take a step back, we have some kind of hybrid situation here, where most of the time, for most of the
users, we actually want eventual consistency, but we need to have exceptions. This is what we call Read-after-Write
consistency.

## Read-after-Write consistency

There are many ways of implementing this read-after-write consistency. We're discussing one example approach
for managed and subscribed projections each:

#### Managed Projections and selective update

When using managed projections you can call `factus.update(myManagedUserCount);` whenever you want.
If you have chosen to update it regularly, the first idea would be to add an additional update call,
whenever a relevant fact was published (last thing in the write path).
The effect would be, that once a user has saved and is redirected to the list view again, the model
for the list view will be updated as the redirect only happens _after_ the update call succeeded.
This would also only work for externalized models and might be wasteful. Basically it mimics a
subscribed projection in a way (apply change as soon as it happened), but gives you the control necessary
to make the user in question (the writer) wait for the model to be updated.

#### Subscribed Projections and additional waiting

When using subscribed projections you have no control over when the fact arrives at the model. So
in order to make the data appear consistent to the user who just changed something, you'd need to
defer his query after the write _until the relevant change has been applied_.

This can be done using the `factus.waitFor(mySubscribedProjection, factIdOfTheFactToWaitFor [, duration])` method.
In order to achieve this, you'd need to return the id of the fact published to the client and make it
come back for a read with this id (or a token that you asssociate with this id).
This way, you could implement your queries to wait for the model to get consistent to the change represented by
the id.

For in-depth discussion of how to use this approach,
see [Subscribed Projections](/usage/factus/projections/types/subscribed-projection/)
