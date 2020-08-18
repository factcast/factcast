+++
draft = false
title = "Concepts"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"
weight = 15
parent = ""
identifier = "concept"


+++

## Concept

In order to use `FactCast` effectively it is necessary to have an overview of the concepts and to understand how `FactCast` might differ from other solutions you are already familiar with. So let's take a look at the basics:

### Write (publish)

With `FactCast` you can *publish* `Facts` which will be written into a log. You can publish a single `Fact` as well as a list of `Facts` atomically (all-or-none).

With optimistic locking you can use conditional publishing, which is based upon aggregates that do not change during the lifecycle of the lock (see [optimistic locking](/usage/java/optimistic_locking/)).

### Read (subscribe)

In order to receive `Facts` you have to subscribe to `FactCast` with a subscription request. This is where `FactCast` significantly differs from other solutions because the subscription request contains the *full specification* of what events to receive. This means that no server-side administration is needed, nor any prior knowledge about the streams where to publish the `Facts` into.

{{%alert theme="danger"%}} TODO see `SubscriptionRequest` {{% /alert%}}

In addition to the specification of events to read, the `SubscriptionRequest` also specifies the events to skip (e.g. due to previous consumption). The request also defines how to deal with `Facts` being published in the future.

{{%alert theme="success" %}} *Note that `Facts` are always guaranteed to be sent in the order they were published.* {{% /alert %}}

The three usual subscription models and their corresponding use cases are:

| Subscription Type | Description |
|:--|:--|
| Follow | This covers the 80% of the use cases. Here the consumer catches up with `Facts` from the past and also receives `Facts` in the future *as they are published*. <p>On subscription the consumer sends the `id` of the last event processed and gets every `Fact` that matches the specification and has been published *after* this last known `Fact`.</p>|
| Catchup | <p>This subscription catches up with past events but does not receive any new `Facts` in the future.</p> <p>A usual use case for this subscription is a write model that needs to collect all kinds of information about a specific aggregate in order to validate or to reject an incoming command.</p>|
| Ephemeral | The consumer does not catch up with past events, but receives matching `Facts` in the future. <p>A possible use case is e.g. cache invalidation. Not suitable for read models.</p> |

All these subscription types rely on a streaming transport which uses (at the time of writing) GRPC.

### Read (fetch)

In some situations the bandwidth of the consumption has to be reduced. This can happen if either there are too many consumers interested in the same `Fact` or consumers keep receiving the same `Facts` (e.g. catchup subscriptions without snapshotting). Pushing **only 'ids' (or URLs)** instead of complete `Facts` can improve the performance. Depending on the protocol being used HTTP-Proxies or local caches can also be applied for further performance enhancement.
