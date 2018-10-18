# Change Log

## [upcoming](https://github.com/Mercateo/factcast/tree/upcoming) (2018-10-18)
[Full Changelog](https://github.com/Mercateo/factcast/compare/factcast-0.0.15...upcoming)

**Fixed bugs:**

- Bug: GRPC API Accepts publishing of Facts without namespace [\#137](https://github.com/Mercateo/factcast/issues/137)

**Closed issues:**

- store-pgsql must not accept facts without namespace [\#145](https://github.com/Mercateo/factcast/issues/145)
- Add LZ4 Codecs according to upstream @GrpcCodec Feature [\#140](https://github.com/Mercateo/factcast/issues/140)

**Merged pull requests:**

- \#145: added constraints for header.ns and header.id, added migration … [\#146](https://github.com/Mercateo/factcast/pull/146) ([uweschaefer](https://github.com/uweschaefer))
- \#140: prepared codec for grpc-spring-boot-starter/issues/96 [\#141](https://github.com/Mercateo/factcast/pull/141) ([uweschaefer](https://github.com/uweschaefer))
- \#137: enforce namespace attribute on publish [\#138](https://github.com/Mercateo/factcast/pull/138) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.0.15](https://github.com/Mercateo/factcast/tree/factcast-0.0.15) (2018-10-16)
[Full Changelog](https://github.com/Mercateo/factcast/compare/factcast-0.0.14...factcast-0.0.15)

**Fixed bugs:**

- Implement serialOf in grpc server [\#133](https://github.com/Mercateo/factcast/issues/133)

**Merged pull requests:**

- \#135: reset RDS autoconfig to 0.0.6 [\#136](https://github.com/Mercateo/factcast/pull/136) ([uweschaefer](https://github.com/uweschaefer))
- \#133: added serialOf to GRPC Service [\#134](https://github.com/Mercateo/factcast/pull/134) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.0.14](https://github.com/Mercateo/factcast/tree/factcast-0.0.14) (2018-10-09)
[Full Changelog](https://github.com/Mercateo/factcast/compare/factcast-0.0.12...factcast-0.0.14)

**Implemented enhancements:**

- gRPC: add Codec for snappy or lz4 compression [\#77](https://github.com/Mercateo/factcast/issues/77)
- Enable GZIP for REST-API [\#27](https://github.com/Mercateo/factcast/issues/27)

**Fixed bugs:**

- GRPC Service RemoteFactCast is not auto-configured [\#124](https://github.com/Mercateo/factcast/issues/124)

**Closed issues:**

- Cleanup for 0.0.14 release [\#130](https://github.com/Mercateo/factcast/issues/130)
- Upgrade grpc deps to 1.12.0 [\#125](https://github.com/Mercateo/factcast/issues/125)
- Implement GRPC negotiation protocol [\#123](https://github.com/Mercateo/factcast/issues/123)
- Replace favicon... [\#119](https://github.com/Mercateo/factcast/issues/119)
- Automatic changelog generation from Issue & PR Data [\#117](https://github.com/Mercateo/factcast/issues/117)
- Extract REST code to separate repo [\#84](https://github.com/Mercateo/factcast/issues/84)
- unify schema for subscription-request between rest & grpc api [\#20](https://github.com/Mercateo/factcast/issues/20)
- publish schema for subscriptionrequest [\#19](https://github.com/Mercateo/factcast/issues/19)

**Merged pull requests:**

- \#130: cleanup, added guava,pgsql versions to BOM [\#131](https://github.com/Mercateo/factcast/pull/131) ([uweschaefer](https://github.com/uweschaefer))
- \#123: added handshake method that transports a string map & the proto… [\#128](https://github.com/Mercateo/factcast/pull/128) ([uweschaefer](https://github.com/uweschaefer))
- Issue125 [\#127](https://github.com/Mercateo/factcast/pull/127) ([uweschaefer](https://github.com/uweschaefer))
- added autoconfig [\#126](https://github.com/Mercateo/factcast/pull/126) ([uweschaefer](https://github.com/uweschaefer))
- Issue119 [\#121](https://github.com/Mercateo/factcast/pull/121) ([uweschaefer](https://github.com/uweschaefer))
- changed favicon [\#120](https://github.com/Mercateo/factcast/pull/120) ([uweschaefer](https://github.com/uweschaefer))
- \#117: added automatically generated changelog to site [\#118](https://github.com/Mercateo/factcast/pull/118) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.0.12](https://github.com/Mercateo/factcast/tree/factcast-0.0.12) (2018-10-03)
[Full Changelog](https://github.com/Mercateo/factcast/compare/factcast-0.0.10...factcast-0.0.12)

**Closed issues:**

- Move server project to example-server [\#112](https://github.com/Mercateo/factcast/issues/112)
- Provide a convenient solution for version pinning [\#111](https://github.com/Mercateo/factcast/issues/111)
- Upgrade junit dependency to 5.3.1 [\#109](https://github.com/Mercateo/factcast/issues/109)
- Upgrade embedded postgres dependency [\#106](https://github.com/Mercateo/factcast/issues/106)
- Move pg to version 10 [\#99](https://github.com/Mercateo/factcast/issues/99)
- Deploy Milestone 0.10 to maven central [\#88](https://github.com/Mercateo/factcast/issues/88)

**Merged pull requests:**

- Issue84 [\#116](https://github.com/Mercateo/factcast/pull/116) ([uweschaefer](https://github.com/uweschaefer))
- moved rest module to extra project [\#115](https://github.com/Mercateo/factcast/pull/115) ([uweschaefer](https://github.com/uweschaefer))
- moved examples to dedicated directory [\#114](https://github.com/Mercateo/factcast/pull/114) ([uweschaefer](https://github.com/uweschaefer))
- added BOM project for version pinning [\#113](https://github.com/Mercateo/factcast/pull/113) ([uweschaefer](https://github.com/uweschaefer))
- Update maven-\[surefire,failsafe\]-plugin to 2.22.0 [\#110](https://github.com/Mercateo/factcast/pull/110) ([mweirauch](https://github.com/mweirauch))
- \#99: switched tests to embed pgsql 10 [\#108](https://github.com/Mercateo/factcast/pull/108) ([uweschaefer](https://github.com/uweschaefer))
- \#106: updated dependency for pg-embed [\#107](https://github.com/Mercateo/factcast/pull/107) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.0.10](https://github.com/Mercateo/factcast/tree/factcast-0.0.10) (2018-09-11)
[Full Changelog](https://github.com/Mercateo/factcast/compare/factcast-0.0.8...factcast-0.0.10)

**Fixed bugs:**

- Flaky test setup with circleCI [\#59](https://github.com/Mercateo/factcast/issues/59)
- Follow subscriptions no longer work after "Connection to \[url\] refused". [\#56](https://github.com/Mercateo/factcast/issues/56)
- flaky test [\#49](https://github.com/Mercateo/factcast/issues/49)

**Closed issues:**

- An instanceof check is being performed on the caught exception.  Create a separate catch clause for this exception type. [\#104](https://github.com/Mercateo/factcast/issues/104)
- Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes. [\#103](https://github.com/Mercateo/factcast/issues/103)
- Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes. [\#102](https://github.com/Mercateo/factcast/issues/102)
- add repolocal githooks [\#96](https://github.com/Mercateo/factcast/issues/96)
- fix hugo version [\#95](https://github.com/Mercateo/factcast/issues/95)
- Document unique\_identifier [\#92](https://github.com/Mercateo/factcast/issues/92)
- Document .clear in InMemFactStore [\#87](https://github.com/Mercateo/factcast/issues/87)
- Add Version info procedure to GRPC protocol [\#86](https://github.com/Mercateo/factcast/issues/86)
- Avoid embedded postgres if possible [\#85](https://github.com/Mercateo/factcast/issues/85)
- Switch to JUnit5 [\#79](https://github.com/Mercateo/factcast/issues/79)
- add global gzip compression to all grpc communication server -\> client [\#73](https://github.com/Mercateo/factcast/issues/73)
- cannot run Target:  org.factcast.server.grpc.TransportLayerException: null [\#72](https://github.com/Mercateo/factcast/issues/72)
- Unregister call from Eventbus fails unexpectedly [\#71](https://github.com/Mercateo/factcast/issues/71)
- Add GDPR Declaration to website [\#66](https://github.com/Mercateo/factcast/issues/66)
- Upgrade circleci to 2.0 [\#65](https://github.com/Mercateo/factcast/issues/65)
- Adapt to use oss-parent-pom  [\#63](https://github.com/Mercateo/factcast/issues/63)
- Add License plugin to project [\#61](https://github.com/Mercateo/factcast/issues/61)
- Add some Timeouts when working with PGSQL [\#53](https://github.com/Mercateo/factcast/issues/53)
- Migrate all existing Facts by adding meta.\_ser [\#45](https://github.com/Mercateo/factcast/issues/45)

**Merged pull requests:**

- Issue87 [\#105](https://github.com/Mercateo/factcast/pull/105) ([uweschaefer](https://github.com/uweschaefer))
- \#86: added rpc endpoint for protocol version in grpc [\#101](https://github.com/Mercateo/factcast/pull/101) ([uweschaefer](https://github.com/uweschaefer))
- Issue96 [\#100](https://github.com/Mercateo/factcast/pull/100) ([uweschaefer](https://github.com/uweschaefer))
- Issue95 [\#97](https://github.com/Mercateo/factcast/pull/97) ([uweschaefer](https://github.com/uweschaefer))
- Issue79 [\#94](https://github.com/Mercateo/factcast/pull/94) ([uweschaefer](https://github.com/uweschaefer))
- use provided pgsql in circleci [\#93](https://github.com/Mercateo/factcast/pull/93) ([uweschaefer](https://github.com/uweschaefer))
- unique\_identifier added. fixes \#82 [\#91](https://github.com/Mercateo/factcast/pull/91) ([uweschaefer](https://github.com/uweschaefer))
- Issue53 [\#90](https://github.com/Mercateo/factcast/pull/90) ([uweschaefer](https://github.com/uweschaefer))
- In-memory FactStore clear [\#81](https://github.com/Mercateo/factcast/pull/81) ([m-kn](https://github.com/m-kn))
- fixes \#45 [\#76](https://github.com/Mercateo/factcast/pull/76) ([uweschaefer](https://github.com/uweschaefer))
- fixes \#71 [\#75](https://github.com/Mercateo/factcast/pull/75) ([uweschaefer](https://github.com/uweschaefer))
- added global server interceptor [\#74](https://github.com/Mercateo/factcast/pull/74) ([uweschaefer](https://github.com/uweschaefer))
- Ignore .factorypath [\#68](https://github.com/Mercateo/factcast/pull/68) ([mweirauch](https://github.com/mweirauch))
- added DSGVO/GDPR declaration [\#67](https://github.com/Mercateo/factcast/pull/67) ([uweschaefer](https://github.com/uweschaefer))
- adapt oss-parent-pom [\#64](https://github.com/Mercateo/factcast/pull/64) ([uweschaefer](https://github.com/uweschaefer))
- added license plugin, \#61 [\#62](https://github.com/Mercateo/factcast/pull/62) ([uweschaefer](https://github.com/uweschaefer))
- Remove wrapping of SQLException [\#58](https://github.com/Mercateo/factcast/pull/58) ([luk2302](https://github.com/luk2302))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*