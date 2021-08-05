---
title: "Requirements"
menu:
main:
weight: 10
type: docs
---

##### Some of the requirements that lead to the current design of FactStore are

* minimal operational effort needed
* Stateless for simple Fail-over
* Stateless for horizontal scalability (for reading)
* well-known data persistence layer for ease of operation
* well-known data persistence layer to be future proof in the light of the german data protection laws (and yes, that's not a trivial one)
* fast *enough* when writing
* fast for reading
* simple (!) enough for teams with very different tools to be able to integrate with their chosen environment

##### NON-Requirements are

* excessive write performance (as in high speed trading)
* full-blown Application Framework
