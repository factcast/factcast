---
title: "Hitchhiker's Guide To Extending Projection Database Support"
weight: 10
type: docs
---

{{% alert title="Preface" %}}

This guide is targeting an audience that is already informed about the projection types offered by Factus, and about
their concepts.

We suggest to check out the "Projections" section of the [Factus API docs]({{< ref "/usage/factus/projections">}})
before delving into this guide.

{{% / alert %}}

## Introduction

When projections consume a lot of events or are complex to calculate it usually makes sense to persist their state into
an external data store instead of using local projections. This way the state is not lost when the service is restarted,
can be shared between service instances,
and the load on the server is reduced. Factus currently supports Redis, all Datastores that support Spring Transactions
and AWS DynamoDb out of the box.
If you want to use a different data store you can implement your own support for projecting into the desired data store.

---

## Extending Factus's Database Support

In case you want to use a data store that is not already supported by the existing abstract projections to persist your
projection state, you can write your own projection implementation. This section guides you through the considerations
and options you need to keep in mind in this situation.

### Prerequisites

When designing a projection implementation you'll need to consider the following aspects:

1. You'll need to provide some kind of locking mechanism for writing into your datastore, so that only one instance at
   a time can update the projection. (`WriterTokenAware`)
2. Define a place to store your projection's state (the last processed event). For example this can be a table in your
   data store that holds one entry for every projection within your service. (`FactStreamPositionAware`)
3. Does your datastore provide transactionality? If yes, you can leverage this to batch changes together when updating
   your projection in order to increase performance and to provide atomicity to your changes. Otherwise, go with a
   basic projection, but keep in mind that this approach has its limitations in terms of atomicity and throughput.

### General Structure

Projection implementations are usually provided via an abstract class that is extended by the actual projections within
your
services. Apart from your desired class hierarchy, projections need to implement `ExternalizedProjection`
(which includes `Projection`,`FactStreamPositionAware`,`WriterTokenAware` and `Named`).

Let's imagine you implement support for the XYZ data store.

### Projections without Transactional Safety

- Create an `AbstractXYZProjection` that implements `ExternalizedProjection`
- Override the Getter and Setter for the FactStreamPosition, which represents the information up to which fact your
  projection has consumed the fact stream. While the actual implementation will depend on your choice of data store,
  one central table per service can be sufficient.
- Override the `acquireWriteToken` method to provide a locking mechanism for your data store, so that only one instance
  of your service is able to write at a time. This way only the instance that holds the lock can process the fact
  stream and update your projection's state.

### Projections with Transactional Safety

There are two types of transaction aware projections. No matter which one you choose the implementation will mostly be
the same.

#### Transaction Aware Projection

In order to implement the `TransactionAware` interface, the data store needs to support atomicity for multiple writes
within a transaction that can be collectively rolled back in case of failure.

- Create a class `XYZTransactionAdapter` that implements the `TransactionAdapter` interface repective to your data
  store.
- Extend your `AbstractXYZProjection` to `AbstractXYZTxProjection` implementing `TransactionAware`
- Now in order to implement the `TransactionAware` interface, you can use
  - `@Delegate TransactionalBehavior<YourTransactionType> txBehavior = new TransactionalBehavior<>( myXYZTransactionAdapter )`
    to put your XYZTranactionAdapter to good use.

#### Open Transaction Aware Projection

TransactionAware projections that are "Open" share all the characteristic of the Transaction Aware Projection, but they
also provide the
projection access to the transaction representation that is currently in progress, in case it is necessary to interact
with the data store in a transactional manner.

- Implement the `OpenTransactionAware` interface instead of `TransactionAware`.

You can have a look at `AbstractSpringTxProjection` or `AbstractRedisTxProjection` as a template.

Please consider giving back to the community by either re-integrating into the factcast project or by publishing your
extension
as a separate project. Please let us know at `info@factcast.org`.
