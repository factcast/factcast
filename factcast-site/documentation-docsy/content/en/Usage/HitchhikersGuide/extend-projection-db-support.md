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
   1.2. If yes, do you need to read the latest state while adding changes to a transaction?
2.

### General Structure

### Projections without Transaction Safety

- Implement the `Projection` interface

### Transaction Aware Projections

There are two types of transaction aware projections. No matter which one you choose the implementation will mostly the
same.

- Transaction Aware Projection
- Open Transaction Aware Projection

#### Transaction Aware Projection

- Implement the `AbstractTransactionAwareProjection` interface

#### Open Transaction Aware Projection

- Implement the `AbstractOpenTransactionAwareProjection` interface

Projections that are "Open" share all the characteristic of the Transaction Aware Projection, but they also provide the
event handler access to the transaction in progress, so that it can read the latest state. This does not affect your
implementation but requires that the database is able to  
this does not require any changes

- the database needs to support the capability of batching write items (that might affect the same entity) within a transaction that can be collectively rolled back in case of failure.
