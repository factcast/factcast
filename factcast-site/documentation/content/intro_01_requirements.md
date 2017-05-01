+++
draft = false
title = "Requirements"
description = ""



creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "intro"
identifier = "requirements"
weight = 10
+++

##### Some of the requirements that lead to the current design of FactStore are

* based on wellknown technology
* minimal operational effort needed
* Stateless for simple Failover
* Stateless for horizontal scalability (for reading)
* wellknown data persistence layer for ease of operation
* wellknown data persistence layer to be future proof in the light of the german data protection laws (and yes, that's not a trivial one)
* fast **enough** when writing
* fast for reading
* Simple (!) enough for teams with very different tools to be able to integrate with their chosen environment

##### NON-Requirements are

* excessive write performance (as in high speed trading)
* full-blown Application Framework 







