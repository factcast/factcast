# FactCast 

#### is a 'good enough' event store using PostgreSQL for persistence, and offers remoting via GRPC.

**This project is not yet ready for primetime**

It is not yet released, the API may change, the documentation is incomplete.

[![Actions
Status](https://github.com/factcast/factcast/workflows/maven/badge.svg?branch=master)](https://github.com/factcast/factcast/actions)
[![codecov](https://codecov.io/gh/factcast/factcast/branch/master/graph/badge.svg)](https://codecov.io/gh/factcast/factcast)
[![CodeFactor](https://www.codefactor.io/repository/github/factcast/factcast/badge)](https://www.codefactor.io/repository/github/factcast/factcast)
[![MavenCentral](https://img.shields.io/maven-central/v/org.factcast/factcast/0.6.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.factcast%22%20v:0.6)
![Docker Image Version (latest semver)](https://img.shields.io/docker/v/factcast/factcast?label=dockerhub)
<a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img class="inline" src="https://img.shields.io/badge/license-ASL2-green.svg?style=flat">
</a>


... under active development.

## The Problem at hand

In a micro-service world, teams choose their own tools of trade. This is a very important benefit of using Microservices in the first place, and you do not want to mess with this principle. However, where Subsystems communicate with each other (most likely crossing those team borders) you need some common ground. Event Sourcing is a great pattern here (as well as within those subsystems) because of the decoupling effect of its use.

So, what is needed is some technical solution, that everyone can easily agree on, because it forces as little technical dependencies on the clients as possible.
GRPC and similar technological choices provide this solution as well as streaming, so we have all we need. Oh and one thing: Whatever solution we choose to store and stream forward needs to be failure tolerant, somewhat scalable and should pose minimal operational complexity and overhead to an existing system.

This is where some of the existing solutions pose a possible problem:

While all of them are most probably great, when it comes to clustering, backup, data-/application-management and fail-over, none of these are trivial problems and most of them bring their own (certainly great) solution.

Gee, i wish there was a solution, that is flexible, platform neutral and could be operated at scale with what we already know...

[Read more on factcast.org](https://factcast.org)

[Detailed changlelog](https://docs.factcast.org/changelog)


