+++
title = "Filtering"
weight = 70
type = "docs"
+++

When implementing a Projection, you would add handler methods
([methods annotated with either `@Handler` or `@HandlerFor`]({{< ref "handler-parameters.md" >}}))
in order to express, what the projection is interested in.

Factus will look at these methods in order to discover [fact specifications]({{< ref "factspec.md#specification" >}}).
These fact specifications form a query which is sent to the FactCast server to create a fact-stream suited for this
projection.
In detail, for each handler method, a Projector inspects the method's annotations and parameter types
including their annotations to build a
[
`FactSpec`](https://github.com/factcast/factcast/blob/main/factcast-core/src/main/java/org/factcast/core/spec/FactSpec.java)
object.
This object contains at least the `ns`, `type` properties. Optionally the `version` property is set.

If you look at a `FactSpec` however, sometimes it makes sense to use additional filtering possibilities like

- aggregateId
- meta key/value pair (one or more) or even
- JavaScript acting as a predicate.

If for a projection these filters are known in advance, you can use additional annotations to declare them:

- `@FilterByAggId`
- `@FilterByAggIdProperty` (can only be used on aggregates)
- `@FilterByScript`
- `@FilterByMeta` (can be used repeatedly)
- `@FilterByMetaExists` (can be used repeatedly)
- `@FilterByMetaDoesNotExist` (can be used repeatedly)

{{% alert title="Note" %}}
If your filter is dynamic and hence can not be declared statically via these annotations,
use the [`postprocess`]({{< ref "callbacks.md#postprocess">}}) callback instead.
{{% / alert %}}

## Some Examples

### FilterByMeta

Let's say, you only want to receive events that have a meta pair "priority":"urgent" in their headers.
Here, you would use code like:

```java

@Handler
@FilterByMeta(key = "priority", value = "urgent")
protected void apply(UserCreated created) {
    // ...
}
```

This will add the additional filter defined by the `@FilterByMeta` annotation to `FactSpec`.
As a result, the filtering now takes place at the server side instead of
wasteful client side filtering (like in the body of the `apply` method).
Only those Facts will be returned, that have a meta key-value-pair with a key of `priority` and a value of `urgent`.

### FilterByMetaExists

```java

@Handler
@FilterByMetaExists("priority")
protected void apply(UserCreated created) {
    // ...
}
```

This will add the additional filter defined by the `@FilterByMetaExists` annotation to `FactSpec`.
Only those Facts will be returned, that have a meta key-value-pair with a key of `priority` no matter what the value is.
`FilterByMetaDoesNotExist` works the opposite way, of course.

### FilterByAggIdProperty

```java

@Handler
@FilterByAggIdProperty("recommendedByUserId")
protected void apply(UserRecommended event) {
    // ...
}
```

This will add the additional filter defined by the `@FilterByAggIdProperty` annotation to `FactSpec`.
Only those Facts will be returned, that have a payload that contains a path 'recommendedByUserId' that has a UUID value
which
matches the current Aggregate's ID. This is the reason, this kind of filter is only valid on Aggregate projections.
Note that you could also define a dot-separated path like 'references.recommendedByUserId' if that matches your
EventObject.
The use of Array expressions is not allowed here.

This filter is particularly useful, if you want to process events that reference your Aggregate, but only if your
Aggregate has a particular role.

In the above example, without the filter the apply method would receive all
`UserRecommended` Events, no matter if the role of the current Aggregate was **recommender** or **referral**. You would
probably need to check within your apply method programmatically to decide to process or skip the event.
