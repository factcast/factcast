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

When projections consume a lot of events or are complex to calculate it usually makes sense to persist their state in an
external data store instead of using local projections. This way the state is not lost when the service is restarted and the load
on the server is reduced. FactCast currently supports Redis, Spring Tx and AWS DynamoDb out of the box. But if you want to use a
different database you can implement your own projection implementation.

---

## Extending FactCast's Database Support

In case you want to use a database that is not already supported by the existing projections to persist your
projection state, you can write your own projection implementation. This section guides you through the considerations
and options you need to keep in mind in this situation.

### Prerequisites

When designing a projection implementation you'll need to consider the following aspects:

1. Should the projection provide transactional safety across multiple operations?
   1. If yes, do you need to read the latest state while adding changes to a transaction?
2. How to implement locking?
3. Where to store the projection state aka the FactStreamPosition?

### General Structure

Projection implementations are provided via an abstract class that is extended by the actual projections within your
services. Apart from your Db specific implementation you'll need to implement the "FactStreamPositionAware",
"WriterTokenAware" and "Named" interfaces.

### Projections without Transactional Safety

- Implement the `Projection` interface
- Override the Getter and Setter for the FactStreamPosition, which represents the information up to which event your
  projection has consumed the event stream. While the actual implementation will depend on your choice of datastore,
  one central table per service should be sufficient.
- Override the `acquireWriteToken` method to provide a locking mechanism for your datastore, so that only one instance
  of your service is able to write at a time. This way only the instance that keeps the lock can process the event
  stream and update your projection's state.

### Projections with Transactional Safety

There are two types of transaction aware projections. No matter which one you choose the implementation will mostly the
same.

#### Transaction Aware Projection

In order to implement the TransactionAwareProjection the datastore needs to support the capability of batching write
items that might affect the same entity within a transaction that can be collectively rolled back in case of failure.

- Implement the `AbstractTransactionAwareProjection` interface
- Follow the steps for Projections without Transaction Safety
- Override the `begin`, `commit` and `rollback` methods to provide transactional safety for your projection
  while updating. This is necessary to ensure that all changes to the database are either committed or rolled back
  together. Also make sure to define the maximum batch size by overwriting the `maxBatchSizePerTransaction` method,
  which otherwise defaults to 1000.

#### Open Transaction Aware Projection

Projections that are "Open" share all the characteristic of the Transaction Aware Projection, but they also provide the
event handler access to the transaction that is currently in progress, so that it can read the latest state of your data
before updating it.

- Implement the `AbstractOpenTransactionAwareProjection` interface.
