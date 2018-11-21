+++
draft = false
title = "Home"
description = ""
date = "2017-04-24T18:36:24+02:00"


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

+++

<span id="sidebar-toggle-span">
<a href="#" id="sidebar-toggle" data-sidebar-toggle=""><i class="fa fa-bars"></i></a>
</span>

<div id="top-github-link">
  <a class="github-link"
href="https://github.com/uweschaefer/factcast/edit/master/factcast-site/documentation/content/_index.md" target="blank">
    <i class="fa fa-code-fork"></i> Edit this page</a>
</div>



<h1 align="center">FactCast</h1>

## A simple Event-store based on PostgreSQL.

#### This is work in progress and neither the Documentation, nor the code is in a "released" state. Please keep that in mind when poking around.

Source Code is available as [{{%icon fa-github%}}github](https://github.com/uweschaefer/factcast) repository.

The current project health is
<a href="https://circleci.com/gh/Mercateo/factcast"><img class="inline"
src="https://circleci.com/gh/Mercateo/factcast.svg?style=svg" alt="circleci" title="circleci" style="max-width:100%;"></a>


<a href="https://codecov.io/gh/Mercateo/factcast">
  <img style="width: auto; height: auto;" class="inline" src="https://codecov.io/gh/Mercateo/factcast/branch/master/graph/badge.svg" />
</a>
<a href="https://www.codacy.com/app/uwe/factcast?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=uweschaefer/factcast&amp;utm_campaign=badger">
    <img src="https://camo.githubusercontent.com/1c83cab4eec41ad80d9920cba1bc06f849a97b03/68747470733a2f2f6170692e636f646163792e636f6d2f70726f6a6563742f62616467652f47726164652f6464353932316366656238313438326462373266613864396466363830343866" alt="Codacy Badge" data-canonical-src="https://api.codacy.com/project/badge/Grade/dd5921cfeb81482db72fa8d9df68048f" style="max-width:100%;" class="inline">
</a>
<a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.factcast%22">
    <img class="inline" src="https://img.shields.io/maven-central/v/org.factcast/factcast.svg" alt="MavenCentral">
</a>
<a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img class="inline" src="https://img.shields.io/badge/license-ASL2-green.svg?style=flat" >
</a>


## Latest Noteworthy Releases

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

#### 2018-10-09 <a href="https://github.com/Mercateo/factcast/projects/3">0.0.14 (milestone)</a>
  * GRPC API has changed to enable non-breaking changes later.

#### 2018-10-03 <a href="https://github.com/Mercateo/factcast/projects/2">0.0.12 (milestone)</a>
  * Note that the jersey impl of the REST interface has its own <a href="https://github.com/Mercateo/factcast-rest-jersey">place on github now.</a> and got new coordinates: **org.factcast:factcast-server-rest-jersey:0.0.12.** If you use the REST Server, you'll need to change your dependencies accordingly
  * There is a BOM within factcast at org.factcast:factcast-bom:0.0.12 you can use to conveniently pin versions - remember that factcast-server-rest-jersey might not be available for every milestone and is not part of the BOM


### Upcoming 
* <a href="https://github.com/Mercateo/factcast/projects/4">
    0.2.0 (milestone)
</a>
