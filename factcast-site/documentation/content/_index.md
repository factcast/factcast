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

Source Code is available as [github](https://github.com/uweschaefer/factcast) repository.


<p><a href="https://circleci.com/gh/Mercateo/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/c9ca17501cc705dee98e8f7d2b4388f305b424da/68747470733a2f2f636972636c6563692e636f6d2f67682f4d6572636174656f2f66616374636173742e7376673f7374796c653d736869656c64" alt="CircleCI" data-canonical-src="https://circleci.com/gh/Mercateo/factcast.svg?style=shield" style="max-width:100%;"></a>
<a href="https://codecov.io/gh/Mercateo/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/5fcbc7f9522293156036f8a02b9b9af565be5f35/68747470733a2f2f636f6465636f762e696f2f67682f4d6572636174656f2f66616374636173742f6272616e63682f6d61737465722f67726170682f62616467652e737667" alt="codecov" data-canonical-src="https://codecov.io/gh/Mercateo/factcast/branch/master/graph/badge.svg" style="max-width:100%;"></a>
<a href="https://www.codacy.com/app/uwe/factcast?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=uweschaefer/factcast&amp;utm_campaign=badger" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/1c83cab4eec41ad80d9920cba1bc06f849a97b03/68747470733a2f2f6170692e636f646163792e636f6d2f70726f6a6563742f62616467652f47726164652f6464353932316366656238313438326462373266613864396466363830343866" alt="Codacy Badge" data-canonical-src="https://api.codacy.com/project/badge/Grade/dd5921cfeb81482db72fa8d9df68048f" style="max-width:100%;"></a>
<a href="https://www.codefactor.io/repository/github/mercateo/factcast" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/99f829f74702b58bbab1128c5fd2bf18b5a41559/68747470733a2f2f7777772e636f6465666163746f722e696f2f7265706f7369746f72792f6769746875622f6d6572636174656f2f66616374636173742f6261646765" alt="CodeFactor" data-canonical-src="https://www.codefactor.io/repository/github/mercateo/factcast/badge" style="max-width:100%;"></a>
<a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.factcast%22" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/c936d730316aed5ad49805c35c555ba6ee794347/68747470733a2f2f696d672e736869656c64732e696f2f6d6176656e2d63656e7472616c2f762f6f72672e66616374636173742f66616374636173742e737667" alt="MavenCentral" data-canonical-src="https://img.shields.io/maven-central/v/org.factcast/factcast.svg" style="max-width:100%;"></a>
<a href="https://www.apache.org/licenses/LICENSE-2.0" rel="nofollow">
<img class="inline" src="https://camo.githubusercontent.com/e63d202eb7ed9151a9c46eae71f8599e67a26a56/68747470733a2f2f696d672e736869656c64732e696f2f62616467652f6c6963656e73652d41534c322d677265656e2e7376673f7374796c653d666c6174" data-canonical-src="https://img.shields.io/badge/license-ASL2-green.svg?style=flat" style="max-width:100%;">
</a>
<a href="https://dependabot.com" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/37150bda3831e078eefa05f298bc43599981569c/68747470733a2f2f6170692e646570656e6461626f742e636f6d2f6261646765732f7374617475733f686f73743d676974687562267265706f3d4d6572636174656f2f6661637463617374" alt="Dependabot Status" data-canonical-src="https://api.dependabot.com/badges/status?host=github&amp;repo=Mercateo/factcast" style="max-width:100%;"></a>
<a href="https://lgtm.com/projects/g/Mercateo/factcast/alerts/" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/7bfa9ade7f643bdd72d9a5399618b47f250967c5/68747470733a2f2f696d672e736869656c64732e696f2f6c67746d2f616c657274732f672f4d6572636174656f2f66616374636173742e7376673f6c6f676f3d6c67746d266c6f676f57696474683d3138" alt="Total alerts" data-canonical-src="https://img.shields.io/lgtm/alerts/g/Mercateo/factcast.svg?logo=lgtm&amp;logoWidth=18" style="max-width:100%;"></a>
<a href="https://lgtm.com/projects/g/Mercateo/factcast/context:java" rel="nofollow"><img class="inline" src="https://camo.githubusercontent.com/486bda827f724661367e27f10df4a34084f96ba2/68747470733a2f2f696d672e736869656c64732e696f2f6c67746d2f67726164652f6a6176612f672f4d6572636174656f2f66616374636173742e7376673f6c6f676f3d6c67746d266c6f676f57696474683d3138" alt="Language grade: Java" data-canonical-src="https://img.shields.io/lgtm/grade/java/g/Mercateo/factcast.svg?logo=lgtm&amp;logoWidth=18" style="max-width:100%;"></a></p>


## Latest Noteworthy Releases

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

#### 2018-10-09 <a href="https://github.com/Mercateo/factcast/projects/3">0.0.14 (milestone)</a>
  * GRPC API has changed to enable non-breaking changes later.

#### 2018-10-03 <a href="https://github.com/Mercateo/factcast/projects/2">0.0.12 (milestone)</a>
  * Note that the jersey impl of the REST interface has its own <a href="https://github.com/Mercateo/factcast-rest-jersey">place on github now.</a> and got new coordinates: **org.factcast:factcast-server-rest-jersey:0.0.12.** If you use the REST Server, you'll need to change your dependencies accordingly
  * There is a BOM within factcast at org.factcast:factcast-bom:0.0.12 you can use to conveniently pin versions - remember that factcast-server-rest-jersey might not be available for every milestone and is not part of the BOM


### Upcoming 
* <a href="https://github.com/Mercateo/factcast/projects/4">
    0.2.0 (milestone)
</a>
