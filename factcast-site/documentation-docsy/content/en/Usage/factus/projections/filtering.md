+++
title = "Filtering"
weight = 70
type = "docs"
+++

When implementing a Projection, you would add handler methods
([methods annotated with either `@Handler` or `@HandlerFor`]({{< ref "handler-parameters.md" >}}))
in order to express, what the projection is interested in.

Factus will look at these methods in order to discover [fact specifications]({{< ref "factspec.md#specification" >}}).
These fact specifications form a query which is sent to the FactCast server to create a fact-stream suited for this projection.
In detail, for each handler method, a Projector inspects the method's annotations and parameter types
including their annotations to build a
[`FactSpec`](https://github.com/factcast/factcast/blob/master/factcast-core/src/main/java/org/factcast/core/spec/FactSpec.java) object.
This object contains at least the `ns`, `type` properties. Optionally the `version` property is set.

If you look at a `FactSpec` however, sometimes it makes sense to use additional filtering possibilities like

- aggregateId
- meta key/value pair (one or more) or even
- JavaScript acting as a predicate.

If for a projection these filters are known in advance, you can use additional annotations to declare them:

- `@FilterByAggId`
- `@FilterByScript`
- `@FilterByMeta` (can be used repeatedly)

{{% alert title="Note" %}}
If your filter is dynamic and hence can not be declared statically via these annotations,
use the [`postprocess`]({{< ref "callbacks.md#postprocess">}}) callback instead.
{{% / alert %}}

## Example

Let's say, you only want to receive events that have a meta pair "priority":"urgent" in their headers.
Here, you would use code like:

```java
@Handler
@FilterByMeta(key="priority", value="urgent")
protected void apply(UserCreated created) {
  // ...
}
```

This will add the additional filter defined by the `@FilterByMeta` annotation to `FactSpec`.
As a result, the filtering now takes place at the server side instead of
wasteful client side filtering (like in the body of the `apply` method).
