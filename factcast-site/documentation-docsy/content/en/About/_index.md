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

____

{{< rawhtml >}}
<center>
<a href="https://github.com/factcast/factcast/actions"><img src="https://github.com/factcast/factcast/workflows/maven/badge.svg?branch=master" alt="Actions Status"
class="inline"></a>
<a href="https://codecov.io/gh/factcast/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/2863b83e96f773ebd91aa268323339b8b9ce14b6/68747470733a2f2f636f6465636f762e696f2f67682f66616374636173742f66616374636173742f6272616e63682f6d61737465722f67726170682f62616467652e737667" alt="codecov" data-canonical-src="https://codecov.io/gh/factcast/factcast/branch/master/graph/badge.svg" style="max-width:100%;"></a>
<a href="https://www.codefactor.io/repository/github/factcast/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/22d2823679b006ca86c5651006f0372c659f255d/68747470733a2f2f7777772e636f6465666163746f722e696f2f7265706f7369746f72792f6769746875622f66616374636173742f66616374636173742f6261646765" alt="CodeFactor" data-canonical-src="https://www.codefactor.io/repository/github/factcast/factcast/badge" style="max-width:100%;"></a>
<a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.factcast%22%20v:0." rel="nofollow"><img class="inline"
src="https://img.shields.io/maven-central/v/org.factcast/factcast/0.svg" alt="MavenCentral"
data-canonical-src="https://img.shields.io/maven-central/v/org.factcast/factcast/0.svg" style="max-width:100%;"></a>
<a href="https://hub.docker.com/repository/docker/factcast/factcast/tags"><img class="inline" alt="Docker Image Version (latest semver)"
src="https://img.shields.io/docker/v/factcast/factcast?label=dockerhub"></a>
<a href="https://www.apache.org/licenses/LICENSE-2.0" rel="nofollow">
<img  class="inline" src="https://camo.githubusercontent.com/e63d202eb7ed9151a9c46eae71f8599e67a26a56/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f6c6963656e73652d41534c322d677265656e2e7376673f7374796c653d666c6174" data-canonical-src="https://img.shields.io/badge/license-ASL2-green.svg?style=flat" style="max-width:100%;">
</a>
</center>
{{< /rawhtml >}}

____

FactCast is written in Java & Kotlin and serves as a basis for working in a distributed environment with loosly coupled
software systems that communicate over events.


It provides two APIs for the JVM:

1. FactCast client: low-level, un-opinionated GRPC library to publish / subscribe to facts
2. Factus:  high-level, opinionated library working with facts as Objects, as well as abstractions like Aggregates &
   Projections

and also a Schema-Registry that enables FactCast to <b>validate & transform</b> events on the fly.

If your are new here, you might want to read up on the [motivation]({{< ref "motivation.md" >}}) and [design]({{< ref "design.md" >}}) for the project.

The project is [hosted on GitHub](https://github.com/factcast/factcast) and any kind of contribution is very welcome.

If you have questions that are not easily answered by this website, feel free to open a 'question' issue on [github](https://github.com/factcast/factcast/issues?q=is%3Aissue+label%3Aquestion), or ask a question on [gitter](https://gitter.im/factcast/community).
