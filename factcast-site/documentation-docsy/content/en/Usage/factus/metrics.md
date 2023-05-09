---

title: "Metrics"
type: docs
weigth: 1020
------------

Like [the FactCast server]({{< ref "/Setup/metrics.md" >}} "factcast server metrics"), also Factus makes use
of [micrometer.io](https://micrometer.io/) metrics.

### Metric namespaces and their organization

At the time of writing, there are three namespaces exposed:

- `factus.timings`
- `factus.counts`
- `factus.gauges`

Depending on your micrometer binding, you may see a slightly different spelling in your data (like '
factus_timings`, if your datasource has a special meaning for the '.'-character)

The metrics are automatically tagged with

- the emitting class (`class` tag)
- the name of the metric (`name` tag)

### Existing Metrics

At the time of writing (Factcast version 0.3.13) the following metrics are supported:

#### Counted

- `transaction_attempts` - how often was a transaction retried. See [Optimistic Locking]({{< ref "optimistic-locking.md" >}}
  "factus optimistic locking") for more background
- `transaction_abort` - how often was an attempted transaction aborted

#### Gauged

- `fetch_size` - size in bytes of a fetched [Snapshot projection]({{< ref "snapshot-projections.md" >}}
  "factus snapshot projections") or [Aggregate projection]({{< ref "aggregates.md" >}}
  "factus aggregates")

#### Timed

- `managed_projection_update_duration` - duration in milliseconds a [Managed Projection]({{< ref "managed-projection.md" >}}
  "factus managed projection") took to update
- `fetch_duration` - duration in milliseconds it took to fetch a [Snapshot projection]({{< ref "snapshot-projections.md" >}}
  "factus snapshot projections")
- `find_duration` - duration in milliseconds it took to find a specific [Aggregate]({{< ref "aggregates.md" >}}
  "factus aggregates")
- `event_processing_latency` - time difference in milliseconds between a fact was published and a consuming  
  [Subscribed projection]({{< ref "subscribed-projection.md" >}} "factus subscribed projection") was updated

