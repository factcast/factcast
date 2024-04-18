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

An event-sourced application usually performs [two kinds of interactions]({{< ref "/concept">}}) with the FactCast
server:

- It subscribes to facts and builds up use-case specific views of the received data. These use-case specific views are
  called _projections_.
- It publishes new facts to the event log.

Building up _projections_ works on both APIs, low-level and Factus. However, to simplify development, the high-level
Factus API has [explicit support for this concept]({{< ref "/usage/factus/projections/types">}}).

### Why using Projector Plugins?

---

## Using existing Projector Plugins

This section introduces the existing projector plugins that allow to persist the projection state in an external data store using factus.

- Redis (link??)
- Spring Tx (link??)

For interaction with FactCast we are using the low-level API.

---

## Extending Database Support with custom Projector Plugins

In some cases you might want to use a database that is not supported by the existing projector plugins to persist your
projection state. In this case, you can write your own projector plugin in order to add support. This section guides
you through the considerations and options you need to keep in mind, if you plan to implement a custom projector plugin.

### Prerequisites

When designing a projector plugin you'll need to consider the following aspects:

- Should the projection be transaction safe?
  - Open?

### General Structure

### Projections without Transaction Safety

### Transaction Aware Projections

### Open Transaction Aware Projections

- the database needs to support the capability of batching write items (that might affect the same entity) within a transaction that can be collectively rolled back in case of failure.
