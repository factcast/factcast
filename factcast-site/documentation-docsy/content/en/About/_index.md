---
title: "About"

menu:
  main:
    weight: 1

type: "docs-start"

weight: 1
---

<div style="text-align: center; font-size: xx-large;"><i>FactCast is a 'good
enough' Event-store based on PostgreSQL.</i></div>
<div style="text-align: center;">and also</div>
<div style="text-align: center; font-size: x-large;"><i>Factus is an API to write Event-Sourced applications on the JVM using FactCast</i></div>

---

<center>


[![Actions Status](https://github.com/factcast/factcast/workflows/maven/badge.svg?branch=master)](https://github.com/factcast/factcast/actions)
[![codecov](https://codecov.io/gh/factcast/factcast/graph/badge.svg?token=0eHdAKj2ZY)](https://codecov.io/gh/factcast/factcast)
[![CodeFactor](https://www.codefactor.io/repository/github/factcast/factcast/badge)](https://www.codefactor.io/repository/github/factcast/factcast)
[![Maven Central](https://img.shields.io/maven-central/v/org.factcast/factcast/0.7.svg)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.factcast)
[![DockerHub](https://img.shields.io/docker/v/factcast/factcast?label=dockerhub)](https://hub.docker.com/repository/docker/factcast/factcast/tags)
[![License](https://img.shields.io/github/license/factcast/factcast)](https://www.apache.org/licenses/LICENSE-2.0)


</center>


---

FactCast is written in Java & Kotlin and serves as a basis for working in a distributed environment with loosely coupled
software systems that communicate over events.

It provides two APIs for the JVM:

1. FactCast client: low-level, un-opinionated GRPC library to publish / subscribe to facts
2. Factus: high-level, opinionated library working with facts as Objects, as well as abstractions like Aggregates &
   Projections

and also a Schema-Registry that enables FactCast to <b>validate & transform</b> events on the fly.

If you are new here, you might want to read up on the [motivation]({{< ref "motivation.md" >}}) and [design]({{< ref "design.md" >}}) for the project.

The project is [hosted on GitHub](https://github.com/factcast/factcast) and any kind of contribution is very welcome.

If you have questions that are not easily answered by this website, feel free to open a 'question' issue on [GitHub](https://github.com/factcast/factcast/issues?q=is%3Aissue+label%3Aquestion), or ask a question on [gitter](https://gitter.im/factcast/community).
