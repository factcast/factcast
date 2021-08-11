---
title: "Handler Parameters"
type: docs
weigth : 1020
---

Inside projections, Factus uses [methods annotated with `@Handler` or `@HandlerFor`]({{< ref "projections.md#projections-in-general" >}}) 
to process events. These methods allow various parameters, also in combination, which can serve as "input" during event handling.

Common Handler Parameters
-------------------------

| Parameter Type | Description                                                                                         | 
|----------------|-----------------------------------------------------------------------------------------------------|
| `EventObject`  | an instance of a Java class [implementing `EventObject`]({{< ref "introduction.md#eventobjects">}}). This is very common. |
| `Fact`  | Provides access to all [Fact]({{< ref "fact.md">}}) details including header (JSON) and payload (JSON) |
| `FactHeader`  | the [Fact header]({{< ref "fact.md#the-header">}}). Provides access to event namespace, type, version, meta entries and others |
| `UUID`  | the [Fact ID of the Fact header]({{< ref "fact.md#the-header">}}) |
 

Redis Transactional Projection Parameters
------------------------------------------

Additional to these common parameters, handler methods of a Redis based transactional projections require one of the following parameter:  

| Parameter Type | Description                                                                                         | 
|----------------|-----------------------------------------------------------------------------------------------------|
| `RTransaction` | needed in a [Redis transactional projection]({{< ref "redis-transactional-projections.md">}})
| `RBatch` | needed in a [Redis batched projection]({{< ref "redis-batch-projection.md">}})


Handler Examples
----------------

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


HandlerFor Examples
-------------------

The `@HandlerFor` annotation allows direct access to the Fact data like header or payload. However,
as the data comes in JSON format, you must take care of the deserialization yourself.   

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

Full Example
------------

See [here](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/FactusVariousHandlerParametersDemoITest.java) for the full example.