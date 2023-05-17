---
title: "Metrics"
type: docs
weight: 150
---

Being a regular Spring Boot 2+ application, the FactCast Server uses [micrometer.io](https://micrometer.io) as its
metrics emitting/collecting solution. In order to get started collecting the metrics FactCast Server emits, you'll need
to choose a backend/store for your metrics. Micrometer
has [lots of prebuilt bindings to choose from](https://micrometer.io/docs). Please refer to the respective documentation
in the **Setup** section of the micrometer docs.

When it comes to metrics, you'll have to know what you're looking for. There are

- **Server** metrics in FactCast Server as well as
- **Client** metrics in the factcast client and additionally in the
- [factus client library](/usage/factus/metrics).

We're focussing on _Server_ metrics here.

### Metric namespaces and their organization

At the time of writing, there are six namespaces exposed:

- `factcast.server.timer`
- `factcast.server.meter`
- `factcast.store.timer`
- `factcast.store.meter`
- `factcast.registry.timer`
- `factcast.registry.meter`

Depending on your micrometer binding, you may see a slightly different spelling in your data (like '
factcast_store_timer`, if your datasource has a special meaning for the '.'-character)

Furthermore, metrics in operations are automatically tagged with

- an operation name
- a store name ('pgsql' currently) and
- an exception tag ('None' if unset).

### Existing metrics

There are a bunch of metrics already emitted in the server. These metrics can be grouped by type:

- Timers (collecting durations of code execution)
- Meters (collecting metric events, for example, occurrences of errors)

As this list is continuously growing, we cannot guarantee
the documentation's completeness. If you want to see the current list of operations, please look
at [StoreMetrics.java](https://github.com/factcast/factcast/blob/master/factcast-store/src/main/java/org/factcast/store/internal/StoreMetrics.java)
, [RegistryMetrics.java](https://github.com/factcast/factcast/blob/master/factcast-store/src/main/java/org/factcast/store/registry/metrics/RegistryMetrics.java)
,
or [ServerMetrics.java](https://github.com/factcast/factcast/blob/master/factcast-server-grpc/src/main/java/org/factcast/server/grpc/metrics/ServerMetrics.java)
respectively.

At the **time of writing (0.4.3)**, the metrics exposed by the namespaces group `factcast.server` are:

| operation | type    | description                        |
| --------- | ------- | ---------------------------------- |
| handshake | `timer` | Duration of the initial handshake. |

At the **time of writing (0.4.3)**, the metrics exposed by the namespaces group `factcast.store` are:

| operation                  | type    | description                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| -------------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| publish                    | `timer` | Time to publish (write) a fact or a list of facts sent by the client.<br />Ref: [concepts](/concept)                                                                                                                                                                                                                                                                                                                                                              |
| subscribe-follow           | `timer` | Time to create and return a follow subscription (not the actual stream of facts).<br />Ref: [concepts](/concept)                                                                                                                                                                                                                                                                                                                                                  |
| subscribe-catchup          | `timer` | Time to create and return a catchup subscription (not the actual stream of facts).<br />Ref: [concepts](/concept)                                                                                                                                                                                                                                                                                                                                                 |
| fetchById                  | `timer` | Time to get a fact from a given ID.                                                                                                                                                                                                                                                                                                                                                                                                                               |
| serialOf                   | `timer` | Time to get the serial of a fact.                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| enumerateNamespaces        | `timer` | Time to process namespaces enumeration.                                                                                                                                                                                                                                                                                                                                                                                                                           |
| enumerateTypes             | `timer` | Time to process types enumeration.                                                                                                                                                                                                                                                                                                                                                                                                                                |
| getStateFor                | `timer` | Time to get the latest state token for a given fact specification. The state represents the serial of the last fact matching the specifications, and is used by the client to determine whether a fact stream has been updated at a given point in time. Relevant for optimistic locking.<br />Ref: [optimistic locking](/usage/factus/optimistic-locking)                                                                                                        |
| publishIfUnchanged         | `timer` | Time to check against the given state token and possibly publish (write) a fact or a list of facts sent by the client.<br />Ref: [optimistic locking](/usage/factus/optimistic-locking)                                                                                                                                                                                                                                                                           |
| getSnapshot                | `timer` | Time to read a snapshot from the cache.<br />Ref: [snapshots](/usage/factus/projections/snapshotting/)                                                                                                                                                                                                                                                                                                                                                            |
| setSnapshot                | `timer` | Time to create/update a snapshot from the cache.<br />Ref: [snapshots](/usage/factus/projections/snapshotting/)                                                                                                                                                                                                                                                                                                                                                   |
| clearSnapshot              | `timer` | Time to delete a snapshot from the cache.<br />Ref: [snapshots](/usage/factus/projections/snapshotting/)                                                                                                                                                                                                                                                                                                                                                          |
| compactSnapshotCache       | `timer` | Time to delete old entries from the snapshot cache.<br />Ref: [snapshots](/usage/factus/projections/snapshotting/)                                                                                                                                                                                                                                                                                                                                                |
| invalidateStateToken       | `timer` | Time to invalidate the state token used for optimistic locking. The client can abort the transaction and let the server invalidate the token used for consistency.<br />Ref: [optimistic locking](/usage/factus/optimistic-locking)                                                                                                                                                                                                                               |
| notifyRoundTripLatency     | `timer` | Time it takes for a notify on the database to be echoed back to the listener (roundtrip).                                                                                                                                                                                                                                                                                                                                                                         |
| catchupFact                | `meter` | Counts the number of facts returned by a catchup subscription or catchup part of a follow subscription request (e.g. Factus managed projections) managed by the EventStore.<br />Ref: [concepts](/concept)                                                                                                                                                                                                                                                        |
| catchupTransformationRatio | `meter` | [deprecated] Percentage of facts transformed (downcasted/upcasted) by the server in response to a subscribed client. Useful for debugging the amount of overhead due to transforming, for subscription returning a significant amount of facts.<br />Ref: [transformation](/concept/transformation)                                                                                                                                                               |
| missedRoundtrip            | `meter` | If inactive for more than a configured interval (`factcast.store.factNotificationBlockingWaitTimeInMillis`), the server validates the health of the database connection. For this purpose it sends an internal notification to the database and waits to receive back an answer in the interval defined by `factcast.store.factNotificationMaxRoundTripLatencyInMillis`. This metric counts the number of notifications sent without an answer from the database. |
| snapshotsCompacted         | `meter` | Counts the number of old snapshots deleted. This runs as a dedicated scheduled job, configured by `factcast.store.snapshotCacheCompactCron`.<br />Ref: [snapshots](/usage/factus/projections/snapshotting/)                                                                                                                                                                                                                                                       |

At the **time of writing (0.4.3)**, the metrics exposed by the namespaces group `factcast.registry` are:

| operation                        | type    | description                                                                                                                                                                         |
| -------------------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| transformEvent                   | `timer` | Time to transform (upcast/downcast) a single fact.<br />Ref: [transformation](/concept/transformation)                                                                              |
| fetchRegistryFile                | `timer` | Time to retrieve a file from the schema registry.<br />Ref: [facts validation](/concept/schema-registry/)                                                                           |
| refreshRegistry                  | `timer` | Time to execute the schema registry refresh, in order to get the latest schema and transformation updates.                                                                          |
| compactTransformationCache       | `timer` | Time to delete old entries from the transformation cache.                                                                                                                           |
| transformationCache-hit          | `meter` | Counts the number of hits from the transformation cache.                                                                                                                            |
| transformationCache-miss         | `meter` | Counts the number of misses from the transformation cache.                                                                                                                          |
| missingTransformationInformation | `meter` | Counts the number of times that the server was not able to find transformation information from the schema registry.                                                                |
| transformationConflict           | `meter` | Counts the number of conflicts encountered by the server during schema registry update, which is caused by trying to change an existing transformation.                             |
| registryFileFetchFailed          | `meter` | Counts the number of times that the server was not able to get a json file from the schema registry.                                                                                |
| schemaRegistryUnavailable        | `meter` | Counts the number of times that the server was unable to reach the schema registry.                                                                                                 |
| transformationFailed             | `meter` | Counts the number of times that the server failed to transform a fact, using downcasting/upcasting scripts.                                                                         |
| schemaConflict                   | `meter` | Counts the number of conflicts detected by the server on the facts schema returned by the schema registry.                                                                          |
| factValidationFailed             | `meter` | Counts the number of times that the server failed to validate a fact, that is attempted to be published, against the schema registry.                                               |
| schemaMissing                    | `meter` | Counts the number of times that the server detected a schema missing from the schema registry.                                                                                      |
| schemaUpdateFailure              | `meter` | Counts the number of times that the server was unable to update its schema definition from the schema registry, while fetching the initial state of the registry or during refresh. |

### gRPC Metrics

If you're looking for remote calls and their execution times (including marshalling/demarshalling from protobuf), you
can have a look at the metrics automatically added by
the [gRPC library](https://yidongnan.github.io/grpc-spring-boot-starter/en/)
that we use.
The relevant namespaces are:

- `grpcServerRequestsReceived` and
- `grpcServerResponsesSent`

These automatically added metrics only focus on service methods defined in
the [protocol buffer specs](https://github.com/factcast/factcast/blob/master/factcast-grpc-api/src/main/proto/FactStore.proto).
Since a gRPC remote call triggers not everything we want to measure, we introduced additional metrics. When comparing,
for instance, the automatically added durations of gRPC vs. the 'factcast.store.duration', you will find a subtle
difference. The reason for this is that instead of including the gRPC overhead, we chose to only measure the actual
invocations on the FactStore/TokenStore implementation. Depending on your needs, you may want to focus on one or the
other.

### Executor Metrics

Micrometer provides an integration to monitor the default thread pool executor created by Spring Boot.
Under the same namespace `executor.*`, we publish metrics for our own thread pool executors used inside FactCast.

You can distinguish them by the `name` tag. Currently, these are:

- `subscription-factory` - used for incoming new subscriptions
- `fetching-catchup` - used for buffered transformation while using the fetching catchup strategy
- `paged-catchup` - used for buffered transformation while using the paged catchup strategy
- `transformation-cache` - used for inserting/updating entries in the transformation cache (only if you use persisted cache)

See https://micrometer.io/docs/ref/jvm for more information.
