+++
draft = false
title = "Intro"
description = ""
date = "2017-04-24T18:36:24+02:00"


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = ""
identifier = "intro"
weight = 10
+++

**EventSourcing** is a great pattern in many ways for technical as well as business reasons. There are a bunch of exciting EventStores, CEP and CQRS Frameworks / Templates out there.

Amongst others:

* [Greg Young's EventStore – The open-source, functional database with Complex Event Processing in JavaScript.](https://geteventstore.com/)
* [Lagom - Opinionated microservice framework](https://www.lightbend.com/platform/development/lagom-framework)
* [Axon Framework – Java Framework for scalable and high-performance applications.](http://www.axonframework.org/)
* [The Eventuate™ Platform ](http://eventuate.io/)
* [Kafka – for building real-time data pipelines and streaming apps](https://kafka.apache.org/)
* [event-store-commons – a common event store Java interface ](https://github.com/fuinorg/event-store-commons)

All of these have a slightly different focus, but all of them deal with persisting Streams of Events.

### The Problem at hand

In a microservice world, **teams choose their own** tools of trade. This is a very important benefit of using Microservices in the first place, and you do not want to mess with this principle.
However, where Subsystems communicate with each other (most likely corssing those team borders) you need some common ground. EventSourcing is a great pattern here (as well as within those subsystems) because of the decoupling effect of its use. 

##### So, what is needed is some technical solution, that everyone can easily agree on, because it forces as little technical dependencies on the clients as possible.

REST and similar technological choices provide this solution, and if spiced with streaming, we have all we need. 
Oh and one thing: Whatever solution we choose to store and stream forward needs to be failure tolerant, somewhat scalable and should pose minimal operational complexity and overhead to an existing system.

**This** is where some of the above solutions pose a possible problem:

While all of them are most probably great, when it comes to clustering, backup, data-/application-management and failover, none of these are trivial problems and most of them bring their own (certainly great) solution.

##### Gee, i wish there was a solution, that is flexible, platform neutral and could be operated at scale with **what we already know**.

