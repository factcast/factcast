+++
title = "Callbacks"
weight = 60
type = "docs"
+++

When implementing the [Projection interface](https://github.com/factcast/factcast/blob/master/factcast-factus/src/main/java/org/factcast/factus/projection/Projection.java), the user can choose to override these default hook methods for more fine-grained control:

| Method Signature                                               | Description                                                                                                                                                          |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `List<FactSpec> postprocess(List<FactSpec> specsAsDiscovered)` | further filter the handled facts via their [fact specification]({{<ref "factspec.md#specification">}}) including aggregate ID and meta entries                       |
| `void onCatchup()`                                             | invoked after all past facts of the streams were processed. This is a good point to signal that the projection is ready to serve data (e.g. via a health indicator). |
| `void onComplete()`                                            | called when subscription closed without error                                                                                                                        |
| `void onError(Throwable exception)`                            | called when subscription closed after receiving an error. The default impl is to simply logs the error.                                                              |

### postprocess

Annotating your handler methods gives you a convenient way of declaring a projection's interest into particular facts, filtered by `ns`,`type`,pojo to deserialize into, version etc.
This kind of filtering should be sufficient for most of the use-cases. However, annotations have to have constant attributes, so what you cannot do this way is to filter on values that are only available at runtime:
A particular aggregateId or a calculated meta-attribute in the header.

For these usecases the postprocess hook can be used.

The following projection handles `SomethingStarted` and `SomethingEnded` events. When updating the projection, Factus invokes
the `postprocess(...)` method and provides it with a list of `FactSpec` specifications as discovered from the annotations.
If you override the default behavior here (which is just returning the list unchanged), you can intercept and freely modify, add or remove the `FactSpecs`.
In our example this list will contain two entries with the `FactSpec` built from the `SomethingStarted` and 'SomethingEnded' classes respectively.

In the example only facts with a specific aggregate ID and the matching meta entry will be considered,
by adding these filters to every discovered `FactSpec`.

```java
public class MyProjection extends LocalManagedProjection {
  @Handler
  void apply(SomethingStarted event) { // ...
  }
  @Handler
  void apply(SomethingEnded event) { // ...
  }

  @Override
  public @NonNull List<FactSpec> postprocess(@NonNull List<FactSpec> specsAsDiscovered) {
    specsAsDiscovered.forEach(
        spec ->
            // method calls can be chained
            spec.aggId(someAggregateUuid)
                .meta("someMetaAttribute", "someValue"));
    return specsAsDiscovered;
  }
```

{{% alert info %}} Remember that filtering of facts via `FactSpecs` takes place _on the factcast server side_.
{{% /alert %}}

### onCatchup

The Factus API will call the `onCatchup` method after an onCatchup signal was received from the server, indicating that the fact stream is now as near as possible to the end of the FactStream that is defined by the `FactSpecs` used to filter.
Depending on the type of projection, the subscription now went from `catchup` to `follow` mode (for follow subscriptions), or is completed right after (for catchup subscriptions, see `onComplete`).
One popular use-case for implementing the `onCatchup` method is to signal the rest
of the service that the projection is ready to be queried and serve (not too stale) data.
In Spring for instance, a [custom health indicator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.health.writing-custom-health-indicators)
can be used for that purpose.

```java
@Override
public void onCatchup() {
      log.debug("Projection is ready now");
      // perform further actions e.g. switch health indicator to "up"
}
```

### onComplete

The `onComplete` method is called when the server terminated a subscription without any error. It is the last signal a server sends. The default behavior is to ignore this.

### onError

The `onError` method is called when the server terminated a subscription due to an error, or when one of your apply methods threw an exception. The subscription will be closed, either way.
The default behavior is to just log the error.
