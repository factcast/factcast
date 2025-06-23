---
title: "Projection Bootstrapping"
type: docs
weight: 1800
---

Imagine the following scenario:
You have a projection with externalized state (e.g., in a relational database) that processes a large number
of events and supports a user-facing frontend feature (e.g., a reporting table). If you now want to extend this projection
or modify how the projection handles its Facts, it may become necessary to rebuild the projection from scratch, thus
reconsume the entire event stream. This is typically triggered by increasing the projection’s revision.

This situation can be addressed by implementing a mechanism to rebuild the read model asynchronously in the background,
a process we refer to as **bootstrapping**, which is described in this section.

## Considerations

Before diving into how to implement bootstrapping at the projection level, there are several important considerations:

- To bootstrap a projection, it must be deployed and running, with the Factus client connected to the FactCast server so
  it can receive events. At the same time, we must prevent the projection from handling any user-facing queries,
  and of course it must not be used in command handling / business decisions.
- Since depending on your amount of data, and the scope of the projection, bootstrapping may take a significant
  amount of time.

## High level concept

We want to rebuild the projection asynchronously, while its predecessor is still being used.
When the projection is caught up it can be tested, maybe compared to its predecessor and when validated,
it can be used for querying. This in general can be elegantly controlled with a feature-flag, if you want to switch at
runtime.

## Single deployment approach

In order to fulfill the above requirements, we could imaging to just add the new projection as a different class to
our application, deploy, wait for it to catch up, flip a toggle and remove the old projection in the next deployment.

There are some drawbacks with this approach, though:

#### You may run out of good names

Renaming the projection is not necessarily what you're looking for. You just want it to be "the new version".
In order to achieve this, you'd need to provide a non-default name to your new projection & increase the revision.
This might not be obvious to everybody looking at the codebase.

#### Other deployments may interrupt rebuild

Not a problem per se, but if someone else deploys the same application from a different branch, the rebuild is at least
interrupted. Due to the nature of EventSourcing this is not a problem as rebuilding will pick up where it left off, once
the new projection version is deployed again, but it may be confusing.
As a consequence, this needs coordination and thus might block other people's work.

## Separate deployment approach

Taking these considerations into account, we can implement bootstrapping in a way that isolates it from regular
deployments and other projections. This is achieved by deploying a separate instance whose sole purpose is to update the
projection.

This way, you can keep the name of the projection (just change the revision) and merge it to the default branch after
bootstrapping is complete.

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

Once bootstrapping is complete, the service instance can be stopped. Regular deployments can resume as usual at
all time throughout the process.

{{% alert title="Do not subscribe regular projections in a bootstrap-instance" color="warning" %}}
When following this approach, it is important to ensure that no other subscribed projections are active while
bootstrapping is in progress, because they could take over the respective write lock. The reason is that,
once the bootstrapping instance acquires the write lock for a projection, any instance from a regular deployment
that starts in the meantime will be unable to obtain the lock until the bootstrapping instance is shut down.

This could interfere with the update or rollback of other projections in the regularly deployed service during
bootstrapping.
{{% /alert %}}
