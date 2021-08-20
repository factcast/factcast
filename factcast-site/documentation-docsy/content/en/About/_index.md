---
title: "About"


menu:
  main:
    weight: 1

type: docs
weight: 1
---

{{< rawhtml >}}
<a href="https://github.com/factcast/factcast/actions"><img src="https://github.com/factcast/factcast/workflows/maven/badge.svg?branch=master" alt="Actions Status"
class="inline"></a>
<a href="https://codecov.io/gh/factcast/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/2863b83e96f773ebd91aa268323339b8b9ce14b6/68747470733a2f2f636f6465636f762e696f2f67682f66616374636173742f66616374636173742f6272616e63682f6d61737465722f67726170682f62616467652e737667" alt="codecov" data-canonical-src="https://codecov.io/gh/factcast/factcast/branch/master/graph/badge.svg" style="max-width:100%;"></a>
<a href="https://www.codefactor.io/repository/github/factcast/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/22d2823679b006ca86c5651006f0372c659f255d/68747470733a2f2f7777772e636f6465666163746f722e696f2f7265706f7369746f72792f6769746875622f66616374636173742f66616374636173742f6261646765" alt="CodeFactor" data-canonical-src="https://www.codefactor.io/repository/github/factcast/factcast/badge" style="max-width:100%;"></a>
<a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.factcast%22%20v:0.3" rel="nofollow"><img class="inline"
src="https://img.shields.io/maven-central/v/org.factcast/factcast/0.3.svg" alt="MavenCentral"
data-canonical-src="https://img.shields.io/maven-central/v/org.factcast/factcast/0.3.svg" style="max-width:100%;"></a>
<a href="https://hub.docker.com/repository/docker/factcast/factcast/tags"><img class="inline" alt="Docker Image Version (latest semver)"
src="https://img.shields.io/docker/v/factcast/factcast?label=dockerhub"></a>
<a href="https://www.apache.org/licenses/LICENSE-2.0" rel="nofollow">
<img  class="inline" src="https://camo.githubusercontent.com/e63d202eb7ed9151a9c46eae71f8599e67a26a56/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f6c6963656e73652d41534c322d677265656e2e7376673f7374796c653d666c6174" data-canonical-src="https://img.shields.io/badge/license-ASL2-green.svg?style=flat" style="max-width:100%;">
</a>
<a href="https://dependabot.com" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/9067c86d33741a2fa11586e87089c65cdda10ec3/68747470733a2f2f6170692e646570656e6461626f742e636f6d2f6261646765732f7374617475733f686f73743d676974687562267265706f3d66616374636173742f6661637463617374" alt="Dependabot Status" data-canonical-src="https://api.dependabot.com/badges/status?host=github&amp;repo=factcast/factcast" style="max-width:100%;"></a>
{{< /rawhtml >}}

#
# Motivation

## Event Sourcing

is a great pattern in many ways for technical as well as business reasons. There are a bunch of exciting EventStores, CEP and CQRS Frameworks / Templates out there.

Amongst others in no particular order:

* [Greg Young's EventStore – The open-source, functional database with Complex Event Processing in JavaScript.](https://geteventstore.com/)
* [Lagom - Opinionated microservice framework](https://www.lightbend.com/platform/development/lagom-framework)
* [Akka persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html)
* [Axon Framework – Java Framework for scalable and high-performance applications.](http://www.axonframework.org/)
* [Kafka – for building real-time data pipelines and streaming apps](https://kafka.apache.org/)
* [The Eventuate™ Platform ](http://eventuate.io/)
* [event-store-commons – a common event store Java interface ](https://github.com/fuinorg/event-store-commons)
* [vlingo](https://github.com/vlingo)

All of these have a slightly different focus, but all of them deal with persisting, streaming and sometimes processing of Events.

## The Problem at hand

In a micro-service world, **teams choose their own** tools of trade. This is a very important benefit of using Micro-services in the first place, and you do not want to mess with this principle.
However, where Subsystems communicate with each other (most likely corssing those team borders) you need some common ground. Event Sourcing is a great pattern here (as well as within those subsystems) because of the decoupling effect of its use.

##### So, what is needed is some technical solution, that everyone can easily agree on, because it forces as little technical dependencies on the clients as possible.

GRPC and similar technological choices provide this solution including streaming, secure transport in a language agnostic way.
Oh and one thing: Whatever solution we choose to store and stream forward needs to be failure tolerant, somewhat scalable and should pose minimal operational complexity and overhead to an existing system.

**This** is where some of the above solutions pose a possible problem:

While all of them are most probably great, when it comes to clustering, backup, data-/application-management and fail-over, none of these are trivial problems and most of them bring their own (certainly great) solution.

##### Gee, i wish there was a solution, that is flexible, platform neutral and could be operated at scale with **what we already know**.
