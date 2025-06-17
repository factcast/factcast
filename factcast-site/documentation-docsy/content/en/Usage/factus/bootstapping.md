---
title: "Projection Bootstrapping"
type: docs
weight: 1800
---

Imagine the following scenario:
You have a subscribed projection with externalized state (e.g., in a relational database) that processes a large number
of events and supports a user-facing frontend feature (e.g., a reporting table). After making changes to one of the
consumed Facts or modifying how the projection handles its Facts, it becomes necessary to reconsume the entire event
stream from scratch. This is typically triggered by increasing the projection’s revision.

This situation can be addressed by implementing a mechanism to rebuild the read model asynchronously in the background,
a process we refer to as **bootstrapping**, which is described in this section.

## Considerations

Before diving into how to implement bootstrapping at the projection level, there are several important considerations:

- To bootstrap a projection, it must be deployed and running, with the Factus client connected to the FactCast server so
  it can receive events. At the same time, we must prevent the projection from handling any user-facing requests,
  processing messages from queues, executing Quartz jobs, etc.
- Since bootstrapping may take a significant amount of time, it is often best to deploy a standalone version of the
  service. This avoids blocking or interfering with regular deployments during the process.

## Example-Approach

Taking these considerations into account, we can implement bootstrapping in a way that isolates it from regular
deployments and other projections. This is achieved by deploying a separate instance whose sole purpose is to update the
projection.

When following this approach, it is important to ensure that no other subscribed projections are active while
bootstrapping is in progress. The reason is that, once the bootstrapping instance acquires the write lock for a
projection, any instance from a regular deployment that starts in the meantime will be unable to obtain the lock until
the bootstrapping instance is shut down. This could interfere with the update or rollback of other projections in the
regularly deployed service during bootstrapping.

Here is a step-by-step outline of the approach:

- Create a dedicated service instance for bootstrapping the projection. This instance should not be part of the regular
  deployment pipeline but should instead be started by a dedicated deployment job or script.
- Mark the projection for bootstrapping by setting a flag in the configuration or as an environment variable. This flag
  should also disable all other subscribed projections. We recommend passing a tag (e.g., the merge request ID) to the
  bootstrapping configuration to ensure that only one instance is bootstrapping the projection at any given time.
- Configure a dedicated service profile for bootstrapping that disables unrelated components such as Liquibase, Quartz
  jobs, etc.
- Adjust the health check configuration so that the bootstrapping instance is not considered healthy until the process
  is complete. This prevents it from being registered with load balancers or service discovery.

Once bootstrapping is complete, the service instance can be stopped, and regular deployments can resume as usual.
