---

title: "Handler Parameters"
type: docs
weigth: 1020
------------

Inside projections, Factus uses [methods annotated with `@Handler` or `@HandlerFor`]({{< ref "projections.md#projections-in-general" >}})
to process events. These methods allow various parameters, also in combination, which can serve as "input" during event handling.

## Common Handler Parameters

|     Parameter Type      |                                                          Description                                                           | valid on @Handler | valid on @HandlerFor |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------|-------------------|----------------------|
| `Fact`                  | Provides access to all [Fact]({{< ref "fact.md">}}) details including header (JSON) and payload (JSON)                         | yes               | yes                  |
| `FactHeader`            | the [Fact header]({{< ref "fact.md#the-header">}}). Provides access to event namespace, type, version, meta entries and others | yes               | yes                  |
| `UUID`                  | the [Fact ID of the Fact header]({{< ref "fact.md#the-header">}})                                                              | yes               | yes                  |
| `? extends EventObject` | an instance of a concrete class [implementing `EventObject`]({{< ref "introduction.md#eventobjects">}}).                       | yes               | no                   |

## Extras on Redis atomic Projections

Additional to these common parameters, ProjectionLenses can add parameters to be used by handler methods.
For instance handler methods of a @RedisBatched projection should use:

| Parameter Type |                                  Description                                   | valid on @Handler | valid on @HandlerFor |
|----------------|--------------------------------------------------------------------------------|-------------------|----------------------|
| `RBatch`       | needed in a [Redis batched projection]({{< ref "redis-batch-projection.md">}}) | yes               | yes                  |

similar to @RedisTransactional projections that should use:

| Parameter Type |                                          Description                                          | valid on @Handler | valid on @HandlerFor |
|----------------|-----------------------------------------------------------------------------------------------|-------------------|----------------------|
| `RTransaction` | needed in a [Redis transactional projection]({{< ref "redis-transactional-projections.md">}}) | yes               | yes                  |

## Examples

### @Handler

Here are some examples:

```java
// handle the "SomeThingStarted" event.
// deserialization happened automatically
@Handler
void apply(SomethingStarted event) {
    var someValue = event.getSomeProperty();
    ...
}

// handle the "SomethingChanged" event.
// additionally use information from the Fact header
@Handler
void apply(SomethingChanged event, FactHeader header) {
    int eventVersion = header.version();
    String someMetaDataValue = header.meta().get("some-metadata-key");
    ...
}

// use multiple parameters
@Handler
void apply(SomethingReactivated event,
           FactHeader factHeader,
           UUID factId,
           Fact fact) {
    ...
}
```

These examples were all based on handling events which

- [implement the `EventObject` interface]({{< ref "introduction.md#eventobjects">}}) and
- provide their specification details via [the `@Specification` annotation]({{<ref "introduction.md#eventobjects">}}).

The next section introduces a more direct alternative.

### @HandlerFor

The `@HandlerFor` annotation allows only direct access to the Fact data like header or payload without any deserialization.

```java
// handle "SomethingAdded" events in their version 1
// living in the "test" namespace
@HandlerFor(ns = "test", type = "SomethingAdded", version = 1)
void applySomethingAdded(Fact fact) {
    String payload = fact.jsonPayload();
    ...
}

// also here, multiple parameters can be used
@HandlerFor(ns = "test", type = "SomethingRemoved", version = 2)
void applySomethingRemoved(FactHeader factHeader, UUID factId, Fact fact) {
    ...
}
```

### Full Example

See [here](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/FactusVariousHandlerParametersDemoITest.java) for the full example.
