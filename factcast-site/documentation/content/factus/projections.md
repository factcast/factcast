+++
draft = false
title = "Projections"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus"
identifier = "factus-projections"
weight = 5

+++

Before we can look at processing Events, we first have to talk about another abstraction that does not exist in FactCast: `Projection``` 


```java
public interface Projection { ... }
```

In Factus, a Projection is any kind of state that is distilled from processing Events - in other words: `Projection`s process (or handle) events.

There are several kinds of Projections that we need to look at, but here is an overview:

![](../projections.png)

## Projections in general

What projections have in common is, they handle Events (or Facts). In order to express that a projection can have any number of methods annotated with `@Handler` or `@HandlerFor`. These methods must be package-level/protected accessible and can be either on the Projection itself or on a nested (**non-static**) inner class.
A simple example might be:

```java
/**
*   maintains a map of UserId->UserName
**/
public class UserNames implements SnapshotProjection {

    private final Map<UUID, String> existingNames = new HashMap<>();

    @Handler
    void apply(UserCreated created) {
        existingNames.put(created.aggregateId(), created.userName());
    };

    @Handler
    void apply(UserDeleted deleted) {
        existingNames.remove(deleted.aggregateId());
    };
// ...
``` 
Here the EventObject 'UserDeleted' and 'UserCreated' are just basically tuples.



## A word on Snapshots

In EventSourcing a Snapshot is used to memorize an object at a certain point in the EventStream, so that when lateron this object needs to be retrieved again, rather than creating a fresh one and use it to process all relevant events, we can start with the snapshot (that already has the state of the object from before) and just process all the facts that happened since.  
It is easy to see that storing and retrieving snapshots involves some kind of marshalling and unmarshalling, as well as some sort of Key/Value store to keep the snapshots. This store is called a `SnapshotCache`. Factus comes with a default SnapshotCache that uses FactCast to store/retrieve and maintain those cached snapshots. While this works reasonably well and is easy to use, as it does not involve any other piece of infrastructure, you might want to keep an eye on the load/storage imposed by this.
It is very easy provide an implementation of SnapshotCache that uses for instance Redis or memcached for this, so that you keep this load away from FactCast for performance, scalability and in the end also cost efficiency reasons.

## SnapshotProjection 
