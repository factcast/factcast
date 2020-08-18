+++
draft = false
title = "Home"
description = ""
date = "2017-04-24T18:36:24+02:00"


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"

+++

{{< rawhtml >}}
<span id="sidebar-toggle-span">
<a href="#" id="sidebar-toggle" data-sidebar-toggle=""><i class="fa fa-bars"></i></a>
</span>

<div id="top-github-link">
  <a class="github-link"
href="https://github.com/factcast/factcast/edit/master/factcast-site/documentation/content/_index.md" target="blank">
    <i class="fa fa-code-fork"></i> Edit this page</a>
</div>



<h1 align="center">FactCast</h1>

{{< /rawhtml >}}
## A simple Event-store based on PostgreSQL.

#### This is work in progress and neither the Documentation, nor the code is in a "released" state. Please keep that in mind when poking around.

Source Code is available as [github](https://github.com/factcast/factcast) repository.


{{< rawhtml >}}
<a href="https://github.com/factcast/factcast/actions"><img src="https://github.com/factcast/factcast/workflows/maven/badge.svg?branch=master" alt="Actions Status"
class="inline"></a>
<a href="https://codecov.io/gh/factcast/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/2863b83e96f773ebd91aa268323339b8b9ce14b6/68747470733a2f2f636f6465636f762e696f2f67682f66616374636173742f66616374636173742f6272616e63682f6d61737465722f67726170682f62616467652e737667" alt="codecov" data-canonical-src="https://codecov.io/gh/factcast/factcast/branch/master/graph/badge.svg" style="max-width:100%;"></a>
<a href="https://www.codefactor.io/repository/github/factcast/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/22d2823679b006ca86c5651006f0372c659f255d/68747470733a2f2f7777772e636f6465666163746f722e696f2f7265706f7369746f72792f6769746875622f66616374636173742f66616374636173742f6261646765" alt="CodeFactor" data-canonical-src="https://www.codefactor.io/repository/github/factcast/factcast/badge" style="max-width:100%;"></a>
<a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.factcast%22%20v:0.2" rel="nofollow"><img class="inline" src="https://img.shields.io/maven-central/v/org.factcast/factcast/0.2.svg" alt="MavenCentral" data-canonical-src="https://img.shields.io/maven-central/v/org.factcast/factcast/0.2.svg" style="max-width:100%;"></a>
<a href="https://hub.docker.com/repository/docker/factcast/factcast/tags"><img class="inline" alt="Docker Image Version (latest semver)"
src="https://img.shields.io/docker/v/factcast/factcast?label=dockerhub"></a>
<a href="https://www.apache.org/licenses/LICENSE-2.0" rel="nofollow">
<img  class="inline" src="https://camo.githubusercontent.com/e63d202eb7ed9151a9c46eae71f8599e67a26a56/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f6c6963656e73652d41534c322d677265656e2e7376673f7374796c653d666c6174" data-canonical-src="https://img.shields.io/badge/license-ASL2-green.svg?style=flat" style="max-width:100%;">
</a>
<a href="https://dependabot.com" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/9067c86d33741a2fa11586e87089c65cdda10ec3/68747470733a2f2f6170692e646570656e6461626f742e636f6d2f6261646765732f7374617475733f686f73743d676974687562267265706f3d66616374636173742f6661637463617374" alt="Dependabot Status" data-canonical-src="https://api.dependabot.com/badges/status?host=github&amp;repo=factcast/factcast" style="max-width:100%;"></a>
{{< /rawhtml >}}

### Current Version: 0.2.5

## Noteworthy Releases 

### RELEASE 0.2.5
{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.5+">
    0.2.5
</a>
{{< /rawhtml >}}

* important fix for detecting disappearing clients
* self defense agains spinngin clients by refusing excessive reoccuring
  subscription requests
* added PID to subscription requests (compatible change)

see changelog

### RELEASE 0.2.4
{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.4+">
    0.2.4
</a>
{{< /rawhtml >}}

see changelog

### RELEASE 0.2.1
{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.1+">
    0.2.1
</a>
{{< /rawhtml >}}

see changelog

### RELEASE 0.2.0
{{< rawhtml >}}
<a href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.0+">
    0.2.0 (milestone)
</a>
{{< /rawhtml >}}

#### major features 

  * Security improvements, added Roles see [Boot gRPC BasicAuth](/setup/examples/grpc-config-basicauth/)
  * Schema Registry tool [Schema Registry CLI](/usage/java/fc-schema-cli/)
  * Validation of Fact payloads against JSON Schema [Schema validation and Registry](/concept/schema-registry/)
  * Transformation between Versions (Up-/Downcasting) of Fact payloads [Transformation and Registry](/concept/transformation/)

#### minor 

  * Factcast-core does not include shaded jackson anymore
  * dropped Spring Boot1 support
  * dropped InMem impl of FactCast
  * FactCast Server includes lz4 by default

## Past Releases

#### 2019-06-24 0.1.0 (release)
  * Optimistic locking
  * GRPC dynamic compression
  * BASIC_AUTH based secret exchange
  * Spring Security (Reader role)

#### 2018-12-08 0.0.34 (milestone)
  * Automatic retries via (Read)FactCast::retry (thx <a
    href="https://github.com/henningwendt">@henningwendt</a>)

#### 2018-11-21 0.0.32 (milestone)
  * new example projects for TLS usage

#### 2018-11-18 0.0.31 (milestone)
  * Introduces / switches to: JUnit5, Spring Boot 2, Testcontainers
  * new example projects
  * **Not a drop-in replacement: See [Migration Guide](migration)**

#### 2018-10-21 0.0.20 (milestone)
  * added CLI

#### 2018-10-16 0.0.17 (minor bugfix release)
  * added some constraints on facts

#### 2018-10-16 0.0.15 (emergency release)
  * fixed a potential NPE when using RDS

#### 2018-10-09 0.0.14 (milestone)
  * GRPC API has changed to enable non-breaking changes later.

#### 2018-10-03 0.0.12 (milestone)
  * Note that the jersey impl of the REST interface has its own <a href="https://github.com/Mercateo/factcast-rest-jersey">place on github now.</a> and got new coordinates: **org.factcast:factcast-server-rest-jersey:0.0.12.** If you use the REST Server, you'll need to change your dependencies accordingly
  * There is a BOM within factcast at org.factcast:factcast-bom:0.0.12 you can use to conveniently pin versions - remember that factcast-server-rest-jersey might not be available for every milestone and is not part of the BOM


{{< rawhtml >}}
<div align="right">This project is sponsored by
<a href="https://www.prisma-capacity.eu/careers#job-offers"><img
align="bottom" alt="PRISMA European Capacity Platform GmbH" class="inline"
src="/prisma.png"
/></a>
</div>
{{< /rawhtml >}}
