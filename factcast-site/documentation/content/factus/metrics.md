+++
draft = false
title = "Metrics"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus"
identifier = "factus-metrics"
weight = 1050

+++

Like [the Factcast server]({{< ref "/setup/server/metrics.md" >}} "factcast server metrics"), also Factus makes use 
of [micrometer.io](https://micrometer.io/) metrics. Currently the following metrics are provided:

### Metric namespaces and their organization

At the time of writing, there are four namespaces exposed:

* `factus.timings`
* `factus.counts`
* `factus.gauges`

Depending on your micrometer binding, you may see a slightly different spelling in your data (like '
factus_timings`, if your datasource has a special meaning for the '.'-character)

The metrics are automatically tagged with 

// TODO
* the emitting class (`class` tag) 

#### Existing Metrics
At the time of writing (TODO add factus version) the following metrics are supported:

Counted
------
  TRANSACTION_ATTEMPTS("transaction_attempts");   optimistic locking, how often retried. executed at the beginning of a transaction
  TRANSACTION_ABORT("transaction_abort"),    how often was a transaction aborted

Gauged
------
fetch_size - size in bytes of the fetched Snapshot projection (including aggregate projection) 
  
Timed
------

managed_projection_update_duration  - update duration in milliseconds
  FETCH_DURATION("fetch_duration"),  - duration in milliseconds to fetch a snapshot projection
  
  TODO:
    FIND_DURATION("find_duration"),
    EVENT_PROCESSING_LATENCY("event_processing_latency");
  
 
