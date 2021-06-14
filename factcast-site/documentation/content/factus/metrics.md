+++
draft = false
title = "Metrics"
description = ""


creatordisplayname = "Maik Toepfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"


parent = "factus"
identifier = "factus-metrics"
weight = 1050

+++

Like [the Factcast server]({{< ref "/setup/server/metrics.md" >}} "factcast server metrics"), also Factus makes use 
of [micrometer.io](https://micrometer.io/) metrics.

### Metric namespaces and their organization

At the time of writing, there are three namespaces exposed:

* `factus.timings`
* `factus.counts`
* `factus.gauges`

Depending on your micrometer binding, you may see a slightly different spelling in your data (like '
factus_timings`, if your datasource has a special meaning for the '.'-character)

The metrics are automatically tagged with 

* the emitting class (`class` tag) 
* the name of the metric (`name` tag)

### Existing Metrics
At the time of writing (Factcast version 0.3.13) the following metrics are supported:

#### Counted
- `transaction_attempts` - how often was a transaction retried. See [Optimistic Locking]({{< ref "/factus/optimistic-locking.md" >}} 
"factus optimistic locking") for more background
- `transaction_abort` - how often was an attempted transaction aborted

#### Gauged
- `fetch_size` - size in bytes of a fetched [Snapshot projection]({{< ref "/factus/snapshot-projections.md" >}} 
"factus snapshot projections") or [Aggregate projection]({{< ref "/factus/aggregates.md" >}} 
"factus aggregates") 
  
#### Timed
- `managed_projection_update_duration`  - duration in milliseconds a [Managed Projection]({{< ref "/factus/managed-projection.md" >}} 
"factus managed projection") took to update
- `fetch_duration` - duration in milliseconds it took to fetch a [Snapshot projection]({{< ref "/factus/snapshot-projections.md" >}} 
"factus snapshot projections")
- `find_duration` - duration in milliseconds it took to find a specific [Aggregate]({{< ref "/factus/aggregates.md" >}} 
"factus aggregates")
- `event_processing_latency` - time difference in milliseconds between a fact was published and a consuming  
[Subscribed projection]({{< ref "/factus/subscribed-projection.md" >}} "factus subscribed projection") was updated
  
 
