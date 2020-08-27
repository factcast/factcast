+++
draft = false
title = "Local Subscribed Projections"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus-projections"
identifier = "factus-projections-local-subscribed"
weight = 450

+++

![](../ph_ls.png)

As a specialization of SubscribedProjections, LocalSubscribedProjections are local to one VM (just like LocalManagedProjections).
This introduces the same problem already discussed with LocalManagedProjections: An possible inconsistency between nodes.

LocalSubscribedProjections already provide locking (trivial) and state awareness, so they a very easy to use/extend. 
