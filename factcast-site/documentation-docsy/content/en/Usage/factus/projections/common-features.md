+++
title = "Common Features"
weight = 60
type = "docs"
+++

For a more fine-grained control of a projection, these optional hook methods can be implemented:

| Method Signature                                 | Description            |
|--------------------------------------------------|------------------------|
| `List<FactSpec> postprocess(List<FactSpec> specsAsDiscovered)` | further filter the handled events via their [fact specification]({{<ref "factspec.md#specification">}}) including aggregate ID and meta entries | 
| `void onCatchup()`                                | invoked after all past events of the event streams were processed. This is a good point to signal that the projection is ready to serve data (e.g. via a health indicator). |
| `void onComplete()`                              | called when event processing completed. TODO how to differenciate between this and onCatchup?  |
| `void onError(Throwable exception)`              | called when an error occurred during event handling. The [provided default implementation](https://github.com/factcast/factcast/blob/master/factcast-factus/src/main/java/org/factcast/factus/projection/Projection.java) simply logs the error. |

Example
-------

### postprocess

The following projection handles `SomethingStarted` events. When updating the projection, Factus also invokes 
the `postprocess(...)` method and provides it with a list of discovered Fact specifications. 
In our example this list will contain one entry with the `FactSpec` of the `SomethingStarted` event.

Having the Fact specification at hand allows us to further filter the event stream. In the example
only events with a specific aggregate ID and two matching meta entries will be considered.  

```java
public class MyProjection extends LocalManagedProjection {
  ...
  @Handler
  void apply(SomethingStarted event) {
    ...
  }

  @Override
  public @NonNull List<FactSpec> postprocess(@NonNull List<FactSpec> specsAsDiscovered) {
    specsAsDiscovered.forEach(
        spec ->
            // method calls can be chained
            spec.aggId(someAggregateUuid)  
                .meta("someKey", "someValue")
                .meta("someOtherKey", "someOtherValue"));
    return specsAsDiscovered;
  }
```
{{% alert info %}} Filtering of events via `postprocess` takes place *on the event store side*. This can significantly 
reduce the amount of events the client has to process.
{{% /alert %}}

### onCatchup

The `onCatchup` method is invoked after all past events of the event streams were processed. 
This can be helpful for debugging. Also, the `onCatchup` method can contain code to signal the rest
of the service that the projection is ready to serve data. 
In Spring a [custom health indicator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.health.writing-custom-health-indicators)
can be used for that purpose.  

```java
@Override
public void onCatchup() {
      log.debug("Projection ready");
      // perform further actions e.g. switch health indicator to "up"
}
```
