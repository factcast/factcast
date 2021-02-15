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

* `factcast.store.timer`
* `factcast.store.meter`
* `factcast.registry.timer`
* `factcast.registry.meter`

Depending on your micrometer binding, you may see a slightly different spelling in your data (like '
factcast_store_timer`, if your datasource has a special meaning for the '.'-character)

Furthermore, metrics in operations are automatically tagged with 

* an operation name 
* a store name ('pgsql' currently) and 
* an exception tag ('None' if unset).

### Existing metrics

There are a bunch of metrics already emitted in the server. There are different kinds of metrics used:

* Timers (collecting durations of code execution)
* Meters (collecting metric events, for example occurrences of errors)

As this list is constantly growing, we cannot guarantee
completeness of the documentation. If you want to see the current list of operations, please look
at [StoreMetrics.java](https://github.com/factcast/factcast/blob/issue1163/factcast-store-pgsql/src/main/java/org/factcast/store/pgsql/internal/StoreMetrics.java)
.

At the **time of writing (0.3.10)** the store operations that are counted/measured are:

| operation | duration  |
|---|---|
|    publish |  x |
|    subscribe-follow |x |
|    subscribe-catchup | x |
|    fetchById | x |
|    serialOf |  x |
|    enumerateNamespaces | x |
|    enumerateTypes |  x |
|    getStateFor |  x |
|    publishIfUnchanged | x |
|    getSnapshot | x |
|    setSnapshot  | x |
|    clearSnapshot  | x |
|    compactSnapshotCache  | x |
|    notifyDatabaseRoundTrip | x |
|    missedDatabaseRoundtrip | x |  

At the **time of writing (0.3.10)** the registry operations that are counted/measured are:

| operation |  duration  |
|---|---|
| refreshRegistry | x |
| compactTransformationCache | x |
| transformEvent | x  |
| tchRegistryFile | x |

At the **time of writing (0.3.10)** the registry events that are counted are:

| event | meter  |
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

### gRPC Metrics

If you're just looking for remote calls and want to see their execution times (including marshalling/demarshalling from protobuf), you can have a look at the metrics automatically added by the [gRPC library](https://yidongnan.github.io/grpc-spring-boot-starter/en/) we use.
The relevant namespaces are:

* `grpcServerRequestsReceived` and
* `grpcServerResponsesSent`

However, since those only focus on service methods as defined in the [protocol buffer specs](https://github.com/factcast/factcast/blob/master/factcast-grpc-api/src/main/proto/FactStore.proto), and not everything we want to measure is triggered by a remote call, we had to introduce other metrics as well.
When comparing - for instance durations - of gRPC vs the factcast.store.duration' you will find a subtle difference. The reason for this is that instead of including the gRPC overhead, we chose to just measure the actual invocations on the FactStore/TokenStore implementation.

You may want to focus at one or the other, depending on your needs.
