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
on the server is reduced. FactCast comes without of the box support for Redis, Spring Tx and AWS DynamoDb. But if you want to use a
different database you can implement your own projection implementation.

---

## Extending Database Support with custom Projector Plugins

In some cases you might want to use a database that is not already supported by the existing projections to persist your
projection state. In this case, you can write your own projection implementation in order to add support. This section guides
you through the considerations and options you need to keep in mind, if you plan to implement a custom projector plugin.

### Prerequisites

When designing a projection implementation you'll need to consider the following aspects:

- Should the projection be transaction safe?
  - Open?

### General Structure

### Projections without Transaction Safety

- Implement the `Projection` interface

### Transaction Aware Projections

There are two types of transaction aware projections. No matter which one you choose the implementation for your custom XX
will mostly the same.

- Transaction Aware Projections
- Open Transaction Aware Projections

#### Normal Transaction Aware Projections

- Implement the `AbstractTransactionAwareProjection` interface

#### Open Transaction Aware Projections

- Implement the `AbstractOpenTransactionAwareProjection` interface

- the database needs to support the capability of batching write items (that might affect the same entity) within a transaction that can be collectively rolled back in case of failure.
