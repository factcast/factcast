+++
draft = false
title = "Subscribed Projections"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus-projections"
identifier = "factus-projections-subscribed"
weight = 40

+++

![](../ph_s.png)

Snapshot projections and ManagedProjections have one thing in common:
The application actively controls the frequency and time of updates by actively calling a method. While this gives the 
user a maximum of control, it also requires synchronicity. Especially when building query models, this is not
necessarily a good thing. This is where SubscribedProjections come in.

## Definition

A SubscribedProjection is subscribed once to a Fact-stream and is asynchronously updated as soon as the application
receives relevant facts.

Subscribed projections are created by the application and subscribed (once) to factus. As soon as Factus recieves 
matching Facts from the FactCast Server, it updates the projection. The expected latency is obviously dependent on a
variety of parameters, but under normal circumstances is expected to be <100ms, sometimes <10ms.

Its strength however (being updated in the background) also is its weakness: as an application you never know, 
which state the projection is in (eventual consistency).

While this is a perfect projection type for occasionally connected operation or public query models, the inherent 
eventual consistency might be confusing to users, for instance in a *read-after-write* scenario, where the user
does not see his own write. This can lead to suboptimal UX und thus should be used with care after carefully 
considering the trade-offs.

A SubscribedProjection is also StateAware and WriterTokenAware. However the token will not be released as frequently,
as with ManagedProjections. This may lead to "starving" Models, if the process keeping the lock is non-responsive.

Please keep that in mind when implementing the locking facility.
  
  
