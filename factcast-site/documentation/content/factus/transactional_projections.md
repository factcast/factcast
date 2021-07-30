+++
draft = false
title = "Transactional Projections"
description = ""

creatordisplayname = "Maik Töpfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"

parent = "factus-projections"
identifier = "factus-transactional-projections"
weight = 1020
+++

When processing events, a projection has two tasks:
1. persist the changes resulting from the Fact 
2. store the current Fact stream position 

When using an external datastore (e.g. Redis), Factus needs to ensure that these two tasks happen atomically:  either both 
tasks are executed or none. This prevents corrupted data in case e.g. the Redis cluster goes down in the wrong moment.
Factus offers atomic writes through *transactional projections*.  

{{<mermaid>}}
sequenceDiagram
    participant Projection
    participant External Data Store
    Projection->>External Data Store: 1) update projection
    Note right of External Data Store: Inside Transaction
    Projection->>External Data Store: 2) store Fact stream position
{{</mermaid>}}
*In a Transactional Projection, the projection update and the update of the Fact stream position run inside a transaction* 

Factus supports transactions for the following external data stores:
- [data stores supported by Spring Transaction Management]({{< ref "spring_transaction.md" >}}) (e.g. databases via JDBC)
- Redis


Configuration
-------------

Transactional projections are declared via specific annotations. Currently, supported are
- `@SpringTransactional` and
- `@RedisTransactional`

These annotations share two common configuration parameters:

| Parameter Name            |  Description            | Default Value  |
|---------------------------|-------------------------|----------------|
| `size`                    | bulk size               |  50            |
| `timeoutInSeconds`        | timeout in seconds until a transaction is interrupted and rolled back |   30   |


Bulk Processing
---------------
To improve the throughput of processed events, transactional projections support *bulk processing*.

With *bulk processing*   

- a transaction is shared between more than one operation.
- the concrete underlying transaction mechanism (e.g. Spring Transaction Management) can optimize data transmission 
by e.g. locally collecting a certain amount of operations before sending them over the wire.
- skipping unnecessary Fact stream position updates is possible (see next section).

The size of the bulk is configured via the previously mentioned `size` value of the `@SpringTransactional` or `@RedisTransactional` annotation.
 
Note: Bulk processing only takes place [in the `catchup` phase]({{< ref "concept/_index.md">}}). 

Skipping Fact Stream Position Updates
-------------------------------------
Skipping unnecessary updates of the Fact stream position reduces the writes to the external datastore, 
thus improving event-processing throughput.  

The concept is best explained with an example: Suppose we have three events which are processed by a transactional projection and the bulk size set to "1". 
Then, we see the following writes going to the external datastore:

{{<mermaid>}}
sequenceDiagram
    participant Projection
    participant External Data Store
    Projection->>External Data Store: event 1: update projection
    Projection->>External Data Store: event 1: store Fact stream position
    Projection->>External Data Store: event 2: update projection
    Projection->>External Data Store: event 2: store Fact stream position
    Projection->>External Data Store: event 3: update projection
    Projection->>External Data Store: event 3: store Fact stream position
{{</mermaid>}}
*Processing three events with bulk size "1" - each Fact stream position is written*  

As initially explained, here, each update of the projection is accompanied by an update of the Fact stream position. 
To minimize the writes to the necessary minimum, we now increase the bulk size to "3":

{{<mermaid>}}
sequenceDiagram
    participant Projection
    participant External Data Store
    Projection->>External Data Store: event 1: update projection
    Projection->>External Data Store: event 2: update projection
    Projection->>External Data Store: event 3: update projection
    Projection->>External Data Store: event 3: store Fact stream position
{{</mermaid>}}
*Processing three events with bulk size "3" - only the last Fact stream position written*  

This configuration change eliminates two unnecessary intermediate Fact stream position updates. 
Remember, we are in a bulk so it is "all or nothing". In terms of Fact stream position updates, we are just interested 
in the last, most recent position.  

Skipping unnecessary intermediate updates to the Fact stream position, noticeably reduces 
the required writes to the external datastore. Provided a large enough bulk size ("50" is a good default), 
this significantly improves event-processing throughput.
