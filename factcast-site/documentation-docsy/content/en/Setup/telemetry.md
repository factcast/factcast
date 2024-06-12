---
title: "Telemetry"
type: docs
weight: 155
description: Listen to internal telemetry events
---

Starting from factcast version 0.7.9, you can extend your server implementation to listen internal telemetry events.
This can be useful for monitoring and debugging purposes.

The telemetry events are emitted using a dedicated internal [Guava EventBus](https://github.com/google/guava/wiki/EventBusExplained).

## Subscription lifecycle events

Currently, the factcast-store module emits an event on each phase of the subscription lifecycle (see
`org.factcast.store.internal.telemetry.PgStoreTelemetry`):

- `PgStoreTelemetry.Connect` emitted whenever a client connects to the factcast server
- `PgStoreTelemetry.Catchup` emitted whenever the subscription catches up to the current state of the store
- `PgStoreTelemetry.Follow` emitted whenever the subscription started consuming live events
- `PgStoreTelemetry.Close` emitted whenever the client disconnects from the factcast server
- `PgStoreTelemetry.Complete` emitted whenever the subscription completed its lifecycle

Each emitted event contains a `request`, which holds the client's request details.

## How to listen to telemetry events

It boils down to implementing a listener that is able to consume telemetry events, through
`com.google.common.eventbus.Subscribe` annotated methods, and registering it via the `PgStoreTelemetry` bean.

Here is an example:

```java
import com.google.common.eventbus.Subscribe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;

@RequiredArgsConstructor
@Slf4j
public class MyTelemetryListener {

  public MyTelemetryListener(PgStoreTelemetry telemetry) {
    telemetry.register(this);
  }

  @Subscribe
  public void on(PgStoreTelemetry.Connect signal) {
    log.info("FactStreamTelemetry Connect: {}", signal.request());
  }

  @Subscribe
  public void on(PgStoreTelemetry.Close signal) {
    log.info("FactStreamTelemetry Close: {}", signal.request());
  }
}
```

You can check out the full example in the [factcast-example-server-telemetry](https://github.com/factcast/factcast/blob/master/factcast-examples/factcast-example-server-telemetry)
module. That module contains a simple example of how to listen to each subscription lifecycle event, to log the request
details and maintaining a list of _following_ subscriptions, which can be read through the actuator `/info` endpoint.
