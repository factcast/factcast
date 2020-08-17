+++
draft = false
title = "Local Managed Projections"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus-projections"
identifier = "factus-projections-local-managed"
weight = 35

+++

![](../ph_lm.png)

As a specialization of ManagedProjection, a LocalManagedProjection lives within the application 
process and **does not use shared external Databases** to maintain its state.
Relying on the locality, locking and keeping track of the state (position in the eventstream) is 
just a matter of synchronization and an additional field, all being implemented in the abstract 
class `LocalManagedProjection` that you are expected to extend.

Due to the simplicity of use, this kind of implementation would be attractive for starting 
with for non-aggregates, assuming the data held by the Projection is not huge.
 


