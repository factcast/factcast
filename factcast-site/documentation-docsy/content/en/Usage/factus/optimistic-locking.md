+++
title = "Optimistic locking"
weight = 1000
type = "docs"
+++

To make business decisions, you need a model to base those decisions on. In most cases, it is important that this
model is consistent with the facts published at the time of the decision and that the model is up to date.

For example, we want to ensure that the username is unique for the whole system.
In case of (potentially) distributed applications and especially in case of eventsourced applications,
this can be a difficult problem.
For sure what you want to avoid is pessimistic locking for all sorts of reasons,
which leaves us with optimistic locking as a choice.

On a general level, optimistic locking:

- **tries** _to make a change_ and then _to write that change_ **or**
- if something happens in the meantime that could invalidate this change,
  _discard the change_ and **try again** taking the new state into account.

Often this is done by adding a `versionId` or `timestamp` to a particular Entity/Aggregate to detect concurrent changes.

This process can be _repeated until the change is either successful or definitively unsuccessful and needs to be
rejected_.

For our example that would mean:

If a new user registers,

1. check if the username is already taken
   - if so, reject the registration
   - if not, prepare a change that creates the user
2. check if a new user was created in between, and
   - repeat from the beginning if this is the case
   - execute the change while making sure no other change can interfere.

In FactCast/Factus, there is no need to assign a `versionId` or `timestamp` to an aggregate or even have aggregates for
that matter.
All you have to do is to define a _scope_ of an optimistic lock to check for concurrent changes in order to either
discard the prepared changes and try again, or to publish the prepared change if there was no interfering change in
between.

Let's look at the example above:

Consider, you have a `SnapshotProjection` `UserNames` that we have seen before.

```java
public class UserNames implements SnapshotProjection {

  private final Map<UUID, String> existingNames = new HashMap<>();

  @Handler
  void apply(UserCreated created) {
    existingNames.put(created.aggregateId(), created.userName());
  }

  @Handler
  void apply(UserDeleted deleted, FactHeader header) {
    existingNames.remove(deleted.aggregateId());
  }

  boolean contains(String name) {
    return existingNames.values().contains(name);
  }

// ...
```

In order to implement the use case above (enforcing unique usernames), what we can do is basically:

```java
UserNames names=factus.fetch(UserNames.class);
      if(names.contains(cmd.userName)){
      // reject the change
      }else{
      UserCreated prepared=new UserCreated(cmd.userId,cmd.userName));
      // publish the prepared UserCreated Event
      }
```

Now in order to make sure that the code above is re-attempted until there was no interference relevant to the UserNames
Projection and also that the business decision (the simple if clause) is always based on the latest up-to-date data,
Factus offers a simple syntax:

```java
    /**
 * optimistically 'locks' on a SnapshotProjection
 */
<P extends SnapshotProjection> Locked<P> withLockOn(@NonNull Class<P> snapshotClass);
```

Applied to our example that would be

```java

UserRegistrationCommand cmd=...    // details not important here

        factus.withLockOn(UserNames.class)
        .retries(10)                     // optional call to limit the number of retries
        .intervalMillis(50)              // optional call to insert pause with the given number of milliseconds in between attempts
        .attempt((names,tx)->{
        if(names.contains(cmd.userName)){
        tx.abort("The Username is already taken - please choose another one.");
        }else{
        tx.publish(new UserCreated(cmd.userId,cmd.userName));
        }

        });
```

As you can see here, the attempt call receives a BiConsumer that consumes

1. your defined scope, updated to the latest changes in the Fact-stream
2. a `RetryableTransaction` that you use to either publish to or abort.

Note that you can use either a `SnapshotProjection` (including aggregates) as well as a `ManagedProjection` to lock on.
**A `SubscribedProjection` however is not usable here**, due to the fact that they are in nature eventual consistent,
which
breaks a necessary precondition for optimistic locking.

Also note that you should not (and cannot) publish to Factus directly when executing an attempt, as this would
potentially
break the purpose of the optimistic lock, and can lead to infinite loops.

For further details on how to add operations that are executed after successful publishing or on failure handling,
please consult the JavaDocs, or look at the provided examples.
