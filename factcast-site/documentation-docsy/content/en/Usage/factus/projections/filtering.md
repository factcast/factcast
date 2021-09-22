+++
title = "Filtering"
weight = 70
type = "docs"
+++

When implementing a Projection, you'd add handler methods (methods annotated with either `@Handler` or `@HandlerFor`) in order to express, what the projection is interested in.

These Methods will be looked at in order to discover the `FactSpec`s needed for a query to be sent to the FactCast server, that will create a fact-stream suited for this projection.
In order to do this, a Projector inspects the annotations and the parameter types (and their annotations) to build a `FactSpec` Object. This will involve the `ns`, `type` and maybe `version` of a `Factspec`. 

If you look at a `FactSpec` however, sometimes it makes sense to use the additional possibilities of filtering like
* aggregateId
* meta key/value pair (one or more) or even
* JavaScript acting as a predicate.

If those a static for a projection, you can use additional annotations to add those filters:
* `@FilterByAggId`
* `@FilterByScript`
* `@FilterByMeta` (can be used repeatedly)

## Example

Let's say, you only want to receive events that have a meta pair "priority":"urgent" in their headers, you would use code like:

```java
  @Handler
  @FilterByMeta(key="priority", value="urgent")
  protected void apply(UserCreated created) {
    // ...  
  }
```

This will add the additional filter to `FactSpec`, that enables you to implement server side filtering (instead of filtering out on the client side, like in the body of the `apply`-Method)
