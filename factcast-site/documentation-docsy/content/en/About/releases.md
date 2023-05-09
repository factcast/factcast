---

title: "Releases"

type: "docs-noedit"
weight: 100000
--------------

Only important releases are mentioned here.

For full overview, you can look at the [changelog](/about/changelog)

---

### RELEASE 0.4.3

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.4.3+">
0.4.3
</a>
{{< /rawhtml >}}

#### Fixes

Factus: Important fix regarding threading when using Atomicity

{{% alert theme="info" %}} If you are using factus and Atomicity, please update to 0.4.3 asap.
{{% /alert %}}

---

### RELEASE 0.4.2

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.4.2+">
0.4.2
</a>
{{< /rawhtml >}}

#### Fixes

Fix AutoConfiguration and make BinarySnapshotSerializer configurable
ConcurrentModificationException in SnapshotSerializerSupplier

#### Features

Factus: Add possibility to add meta K/V Pairs to a FactSpec via @Handler or @HandlerFor Annotations #1595

#### Documentation

Document hook methods of each Projection #1591
Hitchhikers Guide to Integration Testing #1160

---

### RELEASE 0.4.1

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.4.1+">
0.4.1
</a>
{{< /rawhtml >}}

##### Performance

- Increase ingestion performance
- Reduce load on follow subscriptions

##### Important bugfix

- Fixes missing "\_ser" and "\_ts" attributes in header
- see migration guide if you have been on 0.4.0 before

---

### RELEASE 0.4.0

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.4.0+">
0.4.0
</a>
{{< /rawhtml >}}

#### Feature

Release focuses on stability & performance

Stability

- complete error handling overhaul
- application level Keepalive & Retry
- factus atomic (transactional) processing
- use of GraalJS for transformation (as Nashorn is deprecated)
- move to Java11 (for the server side of FactCast)

Performance

- Tail indexing & Fast-Forward
- FETCHING catchup strategy
- Factus bulk processing
- show progress on factus bulk application

... and a documentation overhaul. For details, see the changelog.

---

### RELEASE 0.3.0

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.3.0+">
0.3.0
</a>
{{< /rawhtml >}}

#### Feature

- high-level API called [Factus](/usage/factus) that should make application code **MUCH** easier when using FactCast
- plenty of new modules:
  - factcast-test
  - factcast-factus
  - factcast-factus-event
  - factcast-factus-bin-snapser
  - factcast-itests-factus
  - factcast-snapshotcache-redisson
- locking now based on arbitrary `FactSpecs` rather than only `aggIds`

#### Fix / Maint

- fix important bug screwing with the Fact order in catchup phase if you have >1000 Facts to catch up to (thx, @dibimer)
- the usual dependency upgrades
- added switch to allow updates of SchemaRegistry (not a good idea in production, but handy on other stages)

---

### RELEASE 0.2.5

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.5+">
0.2.5
</a>
{{< /rawhtml >}}

- important fix for detecting disappearing clients
- self defense agains spinngin clients by refusing excessive reoccuring
  subscription requests
- added PID to subscription requests (compatible change)

---

### RELEASE 0.2.4

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.4+">
0.2.4
</a>
{{< /rawhtml >}}

---

### RELEASE 0.2.1

{{< rawhtml >}}
<a
href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.1+">
0.2.1
</a>
{{< /rawhtml >}}

---

### RELEASE 0.2.0

{{< rawhtml >}}
<a href="https://github.com/factcast/factcast/issues?q=is%3Aissue+milestone%3A0.2.0+">
0.2.0 (milestone)
</a>
{{< /rawhtml >}}

#### major features

- Security improvements, added Roles see [Boot gRPC BasicAuth](/setup/grpc-config-basicauth)
- Schema Registry tool [Schema Registry CLI](/usage/lowlevel/cli/fc-schema-cli/)
- Validation of Fact payloads against JSON Schema [Schema validation and Registry](/concept/schema-registry/)
- Transformation between Versions (Up-/Downcasting) of Fact payloads [Transformation and Registry](/concept/transformation/)

#### minor

- FactCast-core does not include shaded jackson anymore
- dropped Spring Boot1 support
- dropped InMem impl of FactCast
- FactCast Server includes lz4 by default

---

## Past Releases

#### 2019-06-24 0.1.0 (release)

- Optimistic locking
- GRPC dynamic compression
- BASIC_AUTH based secret exchange
- Spring Security (Reader role)

#### 2018-12-08 0.0.34 (milestone)

- Automatic retries via (Read)FactCast::retry (thx <a
  href="https://github.com/henningwendt">@henningwendt</a>)

#### 2018-11-21 0.0.32 (milestone)

- new example projects for TLS usage

#### 2018-11-18 0.0.31 (milestone)

- Introduces / switches to: JUnit5, Spring Boot 2, Testcontainers
- new example projects
- **Not a drop-in replacement: See [Migration Guide](/about/migration)**

#### 2018-10-21 0.0.20 (milestone)

- added CLI

#### 2018-10-16 0.0.17 (minor bugfix release)

- added some constraints on facts

#### 2018-10-16 0.0.15 (emergency release)

- fixed a potential NPE when using RDS

#### 2018-10-09 0.0.14 (milestone)

- GRPC API has changed to enable non-breaking changes later.

#### 2018-10-03 0.0.12 (milestone)

- Note that the jersey impl of the REST interface has its own <a href="https://github.com/Mercateo/factcast-rest-jersey">place on github now.</a> and got new coordinates: **org.factcast:factcast-server-rest-jersey:0.0.12.** If you use the REST Server, you'll need to change your dependencies accordingly
- There is a BOM within FactCast at org.factcast:factcast-bom:0.0.12 you can use to conveniently pin versions - remember that factcast-server-rest-jersey might not be available for every milestone and is not part of the BOM
