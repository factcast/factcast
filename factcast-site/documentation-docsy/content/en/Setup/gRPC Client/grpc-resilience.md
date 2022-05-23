---
title: "gRPC Resilience"
type: docs
weight: 210
---

## Resilience approach

In order to make it easier for clients to deal with errors, we try to mitigate connection or network errors,
or RetryableExceptions in general by just retrying.
There are two types of gRPC communications within the FactCast gRPC API:

* synchronous, request / response
* asynchronous, request / streaming response

While the first can be mitigated easily by retrying the call, things get more complicated in a asynchronous, streaming
scenario.
Imagine a subscription to particular facts (let's say 10) from scratch, where after 5 successfully received facts
the network connection fails. Now simply retrying would mean to receive those 5 facts again, which is not only wasteful,
but also hard to handle, as you'd need to skip those rather than process them a second time.
Here the FactCast gRPC client keeps track of the facts successfully processed and resubscribes to the ones missing.
In this example, it'll try to subscribe to the same factstream but starting after the fifth fact.

Resilience is supposed to "just work" and let you deal with just the non-transient errors.
This is why it is enabled by default with sane defaults.

If you want to disable it completely for any reason, you always can use

```
factcast.grpc.client.resilience.enabled=false
```
See [properties](/setup/properties) for the defaults.
