---
title: "Projection Bootstrapping"
type: docs
weight: 1800
---

Imagine the following scenario:
You have a subscribed projection with externalized state (e.g. in a relational DB) which contains a lot of events and
backs a user-facing functionality in the frontend (e.g. reporting table). After we made some changes to one of the
consumed Facts or changed the way the projection handles its Facts it's required to reconsume all events, which is
initiated by increasing its revision.
The problem is, depending on the size of your Fact Stream and the complexity of the event handlers, a projection might
take a long time (several hours or even days) to fully read the entire history from scratch. But we neither want to
prolong the deployment of the new service instance until the projection caught up, nor ignore the fact that customer
experience is impacted because our reporting table will fill up slowly over time.

This scenario can be solved by implementing a way to rebuild the read model asynchronously in the background which we
call **bootstrapping** and that is described in this section.

## Considerations

Before describing the approach how to implement bootstrapping on a projection level, there are some considerations
that need to be taken care of:

- To bootstrap a projection, it needs to be deployed and running and requires that the Factus client is connected to
  the FactCast server so that it can handle events. At the same time, we don't want the projection to handle any
  requests from users, messages from queues, quartz jobs etc.
- The process of bootstrapping can take some time. Therefore, it might be a good option to deploy a standalone version
  of the service. This way it does not interfere with or block regular deployments while it is in progress.

## Example-Approach

Taking into account the considerations above, we can implement bootstrapping in a way that it is isolated from
regular deployments and does not interfere with other projections by deploying a separated instance whoose sole purpose
it is to update the projection.

WWhen taking this approach it is important to ensure that while the projection is bootstrapped no other subscribed
projection should be subscribed. The reason is that once a write lock of another projection is acquired by the
bootstrapping instance, any instance created by a regular deployment that happends in between it won't be able to quire
the lock until the bootstrapping instance is shutdown. This could interfere with any update or rollback of another
projection of the regularly deployed service during bootstrapping.

The following steps outline the approach:

- Create a new service instance dedicated to bootstrapping the projection. This instance should not be started as part
  of the regular deployment process. But for example by a dedicated deployment job or script.
- Mark the projection for bootstrapping by setting a flag in the configuration or environment variable. At the same time
  this flag should disable all other subscribed projections. We recommend pass a tag with the merge request id to the
  bootstrapping configuration to ensure only one instance can be deployed bootstrapping that projection at a time.
- Configure a service profile for bootstrapping that disables all processes like liquibase, quartz jobs, etc.
- Adapt the health check configuration to ensure that the bootstrapping instance is not considered healthy until the
  bootstrapping process is finished. This way it won't be picked up by load balancers or service discovery.

Once the bootstrapping is finished, the service instance can be stopped and the regular deployment process can continue.
