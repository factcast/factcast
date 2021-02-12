+++ 

draft = false 
title = "Boot Server Metrics"
description = ""
date = "2021-02-09018:36:24+02:00"

creatordisplayname = "Uwe Schäfer"
creatoremail = "uwe.schaefer@prisma-capacity.eu"

weight = 128

[menu.main]
parent = "setup"
identifier = "metrics"

+++

## Metrics

Being a regular Spring Boot 2+ application, the FactCast Server uses [micrometer.io](https://micrometer.io) as its
metrics emitting/collecting solution. In order to get started collecting the metrics FactCast Server emits, you'll need
to choose a backend/store for your metrics. Micrometer
has [lots of prebuilt bindings to choose from](https://micrometer.io/docs). Please refer to the respective documentation
in the **Setup** section of the micrometer docs.

When it comes to metrics, you'll have to know what you're looking for. There are 

* **Server** metrics in FactCast Server as well as
* **Client** metrics in the factcast client and additionally in the
* factus client library. 
  
We're focussing on *Server* metrics here.

### Metric namespaces and their organization

At the time of writing, there are four namespaces exposed:

* `factcast.store.operations.duration`
* `factcast.store.operations.count`
* `factcast.registry.duration`
* `factcast.registry.count`

Depending on your micrometer binding, you may see a slightly different spelling in your data (like '
factcast_store_operations_duration`, if your datasource has a special meaning for the '.'-character)

Furthermore, metrics in operations are automatically tagged with 

* an operation name 
* a store name ('pgsql' currently) and 
* an exception tag ('None' if unset).

### Existing metrics

There are a bunch of metrics already emitted in the server. There are different kinds of metrics used:

* Timers (collecting durations of code execution)
* Counters (collecting metric events, for example occurrences of errors)

As this list is constantly growing, we cannot guarantee
completeness of the documentation. If you want to see the current list of operations, please look
at [StoreMetrics.java](https://github.com/factcast/factcast/blob/issue1163/factcast-store-pgsql/src/main/java/org/factcast/store/pgsql/internal/StoreMetrics.java)
.

At the **time of writing (0.3.10)** the store operations that are counted/measured are:

| operation | count  | duration  |
|---|---|---|
|    publish | x | x |
|    subscribe-follow | x | x |
|    subscribe-catchup | x | x |
|    fetchById | x | x |
|    serialOf | x | x |
|    enumerateNamespaces | x | x |
|    enumerateTypes | x | x |
|    getStateFor | x | x |
|    publishIfUnchanged | x | x |
|    getSnapshot | x | x |
|    setSnapshot | x | x |
|    clearSnapshot | x | x |
|    compactSnapshotCache | x | x |
|    notifyDatabaseRoundTrip | x | x |
|    missedDatabaseRoundtrip | x |  |

At the **time of writing (0.3.10)** the registry operations that are counted/measured are:

| operation | count  | duration  |
|---|---|---|
| refreshRegistry | x | x |
| compactTransformationCache | x | x |
| transformEvent | x | x |
| tchRegistryFile | x | x |

At the **time of writing (0.3.10)** the registry events that are counted are:

| event | count  |
|---|---|
|    transformationCache-hit  | x | 
|    transformationCache-miss | x | 
|    missingTransformationInformation | x | 
|    transformationConflict | x | 
|    registryFileFetchFailed | x | 
|    schemaRegistryUnavailable | x | 
|    transformationFailed | x | 
|    schemaConflict | x | 
|    factValidationFailed | x | 
|    schemaMissing | x | 

