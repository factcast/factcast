# Changelog

## [upcoming](https://github.com/factcast/factcast/tree/upcoming) (2021-02-03)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.9.1...upcoming)

**Merged pull requests:**

- authenticate-against-dockerhub [\#1168](https://github.com/factcast/factcast/pull/1168) ([uweschaefer](https://github.com/uweschaefer))

## [0.3.9.1](https://github.com/factcast/factcast/tree/0.3.9.1) (2021-02-03)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.9...0.3.9.1)

**Closed issues:**

- add factcast-test to bom [\#1164](https://github.com/factcast/factcast/issues/1164)
- Document all metrics \(Server and Client\) [\#1155](https://github.com/factcast/factcast/issues/1155)
- incompatible dependencies grpc-\* [\#1166](https://github.com/factcast/factcast/issues/1166)

**Merged pull requests:**

- \#1166: fixed grpc dependencies to 1.31.1 [\#1167](https://github.com/factcast/factcast/pull/1167) ([uweschaefer](https://github.com/uweschaefer))
- add factcast-test to pom [\#1165](https://github.com/factcast/factcast/pull/1165) ([StephanPraetsch](https://github.com/StephanPraetsch))

## [0.3.9](https://github.com/factcast/factcast/tree/0.3.9) (2021-01-30)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.8...0.3.9)

**Implemented enhancements:**

- Add batch Notification type [\#1111](https://github.com/factcast/factcast/issues/1111)
- Verify and test QUEUE catchup strategy [\#1110](https://github.com/factcast/factcast/issues/1110)
- Check if lock is still held in subscribed projections [\#1108](https://github.com/factcast/factcast/issues/1108)

**Closed issues:**

- Introduce FETCHING catchup Strategy [\#1136](https://github.com/factcast/factcast/issues/1136)
- Consider the impact of multimaps for header.meta [\#1128](https://github.com/factcast/factcast/issues/1128)
- Add missing cleanup of 'catchup' table [\#1106](https://github.com/factcast/factcast/issues/1106)
- IllegalArgumentException with micrometer-registry-prometheus [\#1142](https://github.com/factcast/factcast/issues/1142)

**Merged pull requests:**

- \#1142: ensure metrics have the same tags [\#1143](https://github.com/factcast/factcast/pull/1143) ([Mortinke](https://github.com/Mortinke))
- more coverage [\#1140](https://github.com/factcast/factcast/pull/1140) ([uweschaefer](https://github.com/uweschaefer))
- more coverage [\#1139](https://github.com/factcast/factcast/pull/1139) ([uweschaefer](https://github.com/uweschaefer))
- \#1136: FETCHING strategy as default [\#1137](https://github.com/factcast/factcast/pull/1137) ([uweschaefer](https://github.com/uweschaefer))
- minor perf improvements [\#1135](https://github.com/factcast/factcast/pull/1135) ([uweschaefer](https://github.com/uweschaefer))
- \#1107: switched to temp table [\#1134](https://github.com/factcast/factcast/pull/1134) ([uweschaefer](https://github.com/uweschaefer))
- fix npe on class name usage [\#1133](https://github.com/factcast/factcast/pull/1133) ([uweschaefer](https://github.com/uweschaefer))
- Issue1102 speedup integration tests by reusing containers [\#1132](https://github.com/factcast/factcast/pull/1132) ([uweschaefer](https://github.com/uweschaefer))
- Cleanup beforeSnapshot/ afterRestore [\#1131](https://github.com/factcast/factcast/pull/1131) ([uweschaefer](https://github.com/uweschaefer))
- Issue1111 Batch notification [\#1125](https://github.com/factcast/factcast/pull/1125) ([uweschaefer](https://github.com/uweschaefer))
- \#1108: lock liveness test [\#1124](https://github.com/factcast/factcast/pull/1124) ([uweschaefer](https://github.com/uweschaefer))

## [0.3.8](https://github.com/factcast/factcast/tree/0.3.8) (2020-12-15)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.7...0.3.8)

**Implemented enhancements:**

- Factus: lock on a list of FactSpecs \(as avail in factcast interface\) [\#1129](https://github.com/factcast/factcast/issues/1129)

**Closed issues:**

- Introduce callbacks for "beforeSnapshot" and "beforeHandle" & "afterHandle" [\#1123](https://github.com/factcast/factcast/issues/1123)
- Add hashcode/equals to aggregate defaulting to the id [\#1121](https://github.com/factcast/factcast/issues/1121)

**Merged pull requests:**

- \#1129: Locking on specs only - some tests missing, but should be released asap [\#1130](https://github.com/factcast/factcast/pull/1130) ([uweschaefer](https://github.com/uweschaefer))
- \#1121 added hashcode/equals to aggregates [\#1122](https://github.com/factcast/factcast/pull/1122) ([uweschaefer](https://github.com/uweschaefer))
- \#1105: swallow exception while shutting down [\#1116](https://github.com/factcast/factcast/pull/1116) ([uweschaefer](https://github.com/uweschaefer))
- Issue1092 whitelist improvement [\#1103](https://github.com/factcast/factcast/pull/1103) ([samba2](https://github.com/samba2))

## [0.3.7](https://github.com/factcast/factcast/tree/0.3.7) (2020-12-03)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.6...0.3.7)

**Implemented enhancements:**

- Explore use of temp tables instead of catchup table [\#1107](https://github.com/factcast/factcast/issues/1107)
- Tweak AbstractIntegrationTest to reuse containers between classes in order to reduce build/test-time [\#1102](https://github.com/factcast/factcast/issues/1102)
- Implement callbacks & and interface for before/after update/catchup of projections [\#1090](https://github.com/factcast/factcast/issues/1090)
- \#1098: added synchronization on catchup [\#1100](https://github.com/factcast/factcast/pull/1100) ([uweschaefer](https://github.com/uweschaefer))
- \#1090: afterUpdate callback in Snapshot/Managed-projection [\#1099](https://github.com/factcast/factcast/pull/1099) ([uweschaefer](https://github.com/uweschaefer))
- Fixes \#827 and improves other error message [\#1091](https://github.com/factcast/factcast/pull/1091) ([samba2](https://github.com/samba2))

**Fixed bugs:**

- set liberal limits in integrationTest [\#1114](https://github.com/factcast/factcast/issues/1114)
- \#1114: Bandwitdth protection: better fingerprinting, switch to disable… [\#1115](https://github.com/factcast/factcast/pull/1115) ([uweschaefer](https://github.com/uweschaefer))

**Closed issues:**

- schema-registry-cli: Enrich validation result output. [\#827](https://github.com/factcast/factcast/issues/827)
- Suppress metrics for time diff on subscribed projections before catchup. [\#1109](https://github.com/factcast/factcast/issues/1109)
- Suppress error logging in PgListener when shutting down [\#1105](https://github.com/factcast/factcast/issues/1105)
- make sure the update of snapshot projections is wrapped in synchronized in order make 100% sure that state is synced to heap before projection instance goes public   [\#1098](https://github.com/factcast/factcast/issues/1098)
- Factus: Change locking of local projections to synchronize on a private mutex rather than the projection itself  [\#1094](https://github.com/factcast/factcast/issues/1094)
- Add whitelisting capability for schema registry [\#1092](https://github.com/factcast/factcast/issues/1092)

**Merged pull requests:**

- \#1114: static containers and shutdown hook [\#1119](https://github.com/factcast/factcast/pull/1119) ([uweschaefer](https://github.com/uweschaefer))
- \#1040: unified naming [\#1118](https://github.com/factcast/factcast/pull/1118) ([uweschaefer](https://github.com/uweschaefer))
- Log errors that happen while trying to subscribe [\#1113](https://github.com/factcast/factcast/pull/1113) ([BernhardBln](https://github.com/BernhardBln))
- Issue1092 whitelist [\#1097](https://github.com/factcast/factcast/pull/1097) ([samba2](https://github.com/samba2))
- Fixed Scheduled [\#1096](https://github.com/factcast/factcast/pull/1096) ([BernhardBln](https://github.com/BernhardBln))
- \#1109: skip publishing time diff metrics while catching up [\#1117](https://github.com/factcast/factcast/pull/1117) ([uweschaefer](https://github.com/uweschaefer))

## [0.3.6](https://github.com/factcast/factcast/tree/0.3.6) (2020-10-21)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.5...0.3.6)

**Implemented enhancements:**

- Use an explicit getId\(\) call instead of class.getCanonicalName\(\) to avoid conflicts when dealing with anonymous classes. [\#1084](https://github.com/factcast/factcast/issues/1084)
- Use an explicit getId\(\) call instead of class.getCanonicalName\(\) to avoid conflicts when dealing with anonymous classes. [\#1085](https://github.com/factcast/factcast/pull/1085) ([uweschaefer](https://github.com/uweschaefer))

**Fixed bugs:**

- GrpcFactStore: handshake happens too late \(Other beans could already use it\) [\#1086](https://github.com/factcast/factcast/issues/1086)
- \#1086\_grpcfactstore--handshake-happe: initialize on construction [\#1087](https://github.com/factcast/factcast/pull/1087) ([uweschaefer](https://github.com/uweschaefer))

**Merged pull requests:**


## [0.3.5](https://github.com/factcast/factcast/tree/0.3.5) (2020-10-19)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.4...0.3.5)

**Implemented enhancements:**

- Factus: When fetching SnapshotProjections takes a significant time, persist intermediate snapshots now and then in case another thread also fetches, or the fetching is interrupted before it ends. [\#1075](https://github.com/factcast/factcast/issues/1075)
- \#1075 Factus: When fetching SnapshotProjections takes a significant time, persist intermediate snapshots now and then in case another thread also fetches, or the fetching is interrupted before it ends. [\#1082](https://github.com/factcast/factcast/pull/1082) ([uweschaefer](https://github.com/uweschaefer))

**Closed issues:**

- Move metric collection regarding snapshots to snapshotcache [\#1080](https://github.com/factcast/factcast/issues/1080)

**Merged pull requests:**

- \#1080 Move metric collection regarding snapshots to snapshotcache [\#1081](https://github.com/factcast/factcast/pull/1081) ([uweschaefer](https://github.com/uweschaefer))
- adjusted docs regarding automatic projection serial calculation [\#1079](https://github.com/factcast/factcast/pull/1079) ([uweschaefer](https://github.com/uweschaefer))
- JSON serialiser now calculates hash over schema [\#1050](https://github.com/factcast/factcast/pull/1050) ([BernhardBln](https://github.com/BernhardBln))

## [0.3.4](https://github.com/factcast/factcast/tree/0.3.4) (2020-10-14)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.3...0.3.4)

**Implemented enhancements:**

- clear local redis in integration test [\#1071](https://github.com/factcast/factcast/issues/1071)

**Fixed bugs:**

- Unify naming \(SnapshotCache vs SnapshotRepository\) and fix Autoconfiguration for SnapshotCaches [\#1070](https://github.com/factcast/factcast/issues/1070)
- Issue1070 autoconfiguration & name unification of snapshotCaches [\#1072](https://github.com/factcast/factcast/pull/1072) ([uweschaefer](https://github.com/uweschaefer))

**Closed issues:**

- Switch formatting to google format [\#1054](https://github.com/factcast/factcast/issues/1054)
- Factus snapshot deserialization is a fatal error although it doesn't have to [\#1040](https://github.com/factcast/factcast/issues/1040)

**Merged pull requests:**

- \#1071: introduce RedisExtension that clears all keys in redis [\#1073](https://github.com/factcast/factcast/pull/1073) ([otbe](https://github.com/otbe))
- switch to google format [\#1056](https://github.com/factcast/factcast/pull/1056) ([uweschaefer](https://github.com/uweschaefer))

## [0.3.3](https://github.com/factcast/factcast/tree/0.3.3) (2020-09-25)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.2...0.3.3)

**Closed issues:**

- RedissonSnapshotCacheAutoConfiguration doesnt kick in  [\#1052](https://github.com/factcast/factcast/issues/1052)

**Merged pull requests:**

- Fixes 1052 [\#1053](https://github.com/factcast/factcast/pull/1053) ([otbe](https://github.com/otbe))
- Docs updated [\#1045](https://github.com/factcast/factcast/pull/1045) ([BernhardBln](https://github.com/BernhardBln))

## [0.3.2](https://github.com/factcast/factcast/tree/0.3.2) (2020-09-10)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.1...0.3.2)

**Merged pull requests:**

- Fix: FactCastAutoConfiguration also configures EventSerializer [\#1025](https://github.com/factcast/factcast/pull/1025) ([BernhardBln](https://github.com/BernhardBln))

## [0.3.1](https://github.com/factcast/factcast/tree/0.3.1) (2020-09-08)

[Full Changelog](https://github.com/factcast/factcast/compare/0.3.0...0.3.1)

**Fixed bugs:**

- Factus autoconfiguration is always on [\#1023](https://github.com/factcast/factcast/issues/1023)

**Closed issues:**

- Implement filesystem based schemaregistry [\#965](https://github.com/factcast/factcast/issues/965)

**Merged pull requests:**

- Issue1023 @ConditionalOnMissingBean on autoconfiguration [\#1024](https://github.com/factcast/factcast/pull/1024) ([uweschaefer](https://github.com/uweschaefer))
- Issue965 filesystem based schema registry [\#1014](https://github.com/factcast/factcast/pull/1014) ([BernhardBln](https://github.com/BernhardBln))

## [0.3.0](https://github.com/factcast/factcast/tree/0.3.0) (2020-08-30)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.4.3...0.3.0)

**Implemented enhancements:**

- Add property to allow updating of schema from the registry [\#986](https://github.com/factcast/factcast/issues/986)
- binary snapshot serializer [\#1004](https://github.com/factcast/factcast/pull/1004) ([uweschaefer](https://github.com/uweschaefer))
- Isolate integration tests \(wipe postgres in between\) [\#1003](https://github.com/factcast/factcast/pull/1003) ([uweschaefer](https://github.com/uweschaefer))
- \#942 Add Factus as a high-level API to FactCast [\#991](https://github.com/factcast/factcast/pull/991) ([uweschaefer](https://github.com/uweschaefer))

**Fixed bugs:**

- Mixed up fact order in catchup phase 1, if more than 1000 facts are waiting [\#1002](https://github.com/factcast/factcast/issues/1002)
- \#1002: stable order for catchup beyond page-size [\#1006](https://github.com/factcast/factcast/pull/1006) ([uweschaefer](https://github.com/uweschaefer))

**Closed issues:**

- Provide high-level client library for java [\#942](https://github.com/factcast/factcast/issues/942)
- Introduce msgpack as optional format for \(only\) fact serialization [\#928](https://github.com/factcast/factcast/issues/928)

**Merged pull requests:**

- Foo 1526 more coverage [\#1007](https://github.com/factcast/factcast/pull/1007) ([BernhardBln](https://github.com/BernhardBln))
- Micrometer integration with Factus [\#990](https://github.com/factcast/factcast/pull/990) ([edthamm](https://github.com/edthamm))
- \#942\_hook\_for\_tx\_handling: suggestion for a hook [\#989](https://github.com/factcast/factcast/pull/989) ([uweschaefer](https://github.com/uweschaefer))
- \#986: added switch for schemaReplace [\#987](https://github.com/factcast/factcast/pull/987) ([uweschaefer](https://github.com/uweschaefer))
- Foo 1464 unit tests [\#985](https://github.com/factcast/factcast/pull/985) ([BernhardBln](https://github.com/BernhardBln))
- \#959: fix response compression, add some tracing [\#960](https://github.com/factcast/factcast/pull/960) ([uweschaefer](https://github.com/uweschaefer))
- Coordinate Scheduled Tasks between servers via JDBC [\#954](https://github.com/factcast/factcast/pull/954) ([uweschaefer](https://github.com/uweschaefer))
- Issue950 add metrics to roundtrip testing [\#951](https://github.com/factcast/factcast/pull/951) ([uweschaefer](https://github.com/uweschaefer))
- Issue941 - Implement Keepalive roundtrip to detect stale postgres listeners [\#943](https://github.com/factcast/factcast/pull/943) ([samba2](https://github.com/samba2))
- Foo 1527 more unit tests [\#999](https://github.com/factcast/factcast/pull/999) ([BernhardBln](https://github.com/BernhardBln))

## [factcast-0.1.4.3](https://github.com/factcast/factcast/tree/factcast-0.1.4.3) (2020-08-28)

[Full Changelog](https://github.com/factcast/factcast/compare/0.2.5...factcast-0.1.4.3)

**Closed issues:**

- Fix Response Compression [\#959](https://github.com/factcast/factcast/issues/959)
- Coordinate Schemaregistry updates between servers... [\#953](https://github.com/factcast/factcast/issues/953)
- Add metrics for changes in \#941 [\#950](https://github.com/factcast/factcast/issues/950)
- Improve connection testing and keepalive while waiting for notifications [\#941](https://github.com/factcast/factcast/issues/941)
- Add Module for integration Tests including GRPC [\#612](https://github.com/factcast/factcast/issues/612)

## [0.2.5](https://github.com/factcast/factcast/tree/0.2.5) (2020-07-05)

[Full Changelog](https://github.com/factcast/factcast/compare/0.2.4...0.2.5)

**Fixed bugs:**

- Facts in recieve buffer may still be delivered after subscription.close\(\) is called during onNext\(\) [\#907](https://github.com/factcast/factcast/issues/907)

**Closed issues:**

- Extract examples to extra project [\#923](https://github.com/factcast/factcast/issues/923)
- add Coordinated subscription [\#450](https://github.com/factcast/factcast/issues/450)
- Dangling subscriptions [\#937](https://github.com/factcast/factcast/issues/937)
- Optimize startup time of factcast-docker [\#934](https://github.com/factcast/factcast/issues/934)
- restructure modules \(integration tests\) [\#930](https://github.com/factcast/factcast/issues/930)
- Create integration test for validation disabled mode [\#881](https://github.com/factcast/factcast/issues/881)

**Merged pull requests:**

- Issue930  [\#932](https://github.com/factcast/factcast/pull/932) ([uweschaefer](https://github.com/uweschaefer))
- Issue905 [\#929](https://github.com/factcast/factcast/pull/929) ([uweschaefer](https://github.com/uweschaefer))
- Issue907 [\#926](https://github.com/factcast/factcast/pull/926) ([uweschaefer](https://github.com/uweschaefer))
- Issue937 [\#938](https://github.com/factcast/factcast/pull/938) ([uweschaefer](https://github.com/uweschaefer))
- Issue934 optimize startup time of factcast-docker [\#935](https://github.com/factcast/factcast/pull/935) ([uweschaefer](https://github.com/uweschaefer))
- \#881: added no validation test [\#933](https://github.com/factcast/factcast/pull/933) ([uweschaefer](https://github.com/uweschaefer))

## [0.2.4](https://github.com/factcast/factcast/tree/0.2.4) (2020-07-02)

[Full Changelog](https://github.com/factcast/factcast/compare/0.2.3...0.2.4)

**Closed issues:**

- Prevent integration test artifacts from being deployed to central [\#924](https://github.com/factcast/factcast/issues/924)

**Merged pull requests:**

- Issue924 [\#925](https://github.com/factcast/factcast/pull/925) ([uweschaefer](https://github.com/uweschaefer))

## [0.2.3](https://github.com/factcast/factcast/tree/0.2.3) (2020-07-02)

[Full Changelog](https://github.com/factcast/factcast/compare/0.2.2...0.2.3)

## [0.2.2](https://github.com/factcast/factcast/tree/0.2.2) (2020-07-02)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.1...0.2.2)

**Implemented enhancements:**

- Simplify FactSpec generation from a list of classes. [\#888](https://github.com/factcast/factcast/issues/888)

**Fixed bugs:**

- Fix VM base for docker image [\#917](https://github.com/factcast/factcast/issues/917)

**Closed issues:**

- add database generated timestamp to metadata [\#910](https://github.com/factcast/factcast/issues/910)
- Add retryer for IO actions [\#894](https://github.com/factcast/factcast/issues/894)
- Switch CI from circleci to gh actions [\#877](https://github.com/factcast/factcast/issues/877)
- Switch to gitflow maven plugin for release prep [\#921](https://github.com/factcast/factcast/issues/921)
- Move to dockerfile-maven plugin for docker building and releasing [\#912](https://github.com/factcast/factcast/issues/912)
- add fromNullable\(UUID orNull\) to the SubscriptionRequest api [\#908](https://github.com/factcast/factcast/issues/908)

**Merged pull requests:**

- \#921: use gitflow maven plugin [\#922](https://github.com/factcast/factcast/pull/922) ([uweschaefer](https://github.com/uweschaefer))
- \#877: added gh action workflow [\#916](https://github.com/factcast/factcast/pull/916) ([uweschaefer](https://github.com/uweschaefer))
- \#888: FactSpec.from for multiple classes [\#915](https://github.com/factcast/factcast/pull/915) ([uweschaefer](https://github.com/uweschaefer))
- \#917: moved to openjdk8 as a base for the docker image [\#918](https://github.com/factcast/factcast/pull/918) ([uweschaefer](https://github.com/uweschaefer))
- \#912: build and push docker image [\#913](https://github.com/factcast/factcast/pull/913) ([uweschaefer](https://github.com/uweschaefer))
- Issue \#908: added fromNullable to SpecBuilder [\#909](https://github.com/factcast/factcast/pull/909) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.2.1](https://github.com/factcast/factcast/tree/factcast-0.2.1) (2020-06-29)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0...factcast-0.2.1)

**Closed issues:**

- Limit Auto-Reconnection behavior and escalate to application layer early. [\#889](https://github.com/factcast/factcast/issues/889)

**Merged pull requests:**

- Issue910 add Timestamp to server generated fields in metadata of a fact [\#911](https://github.com/factcast/factcast/pull/911) ([uweschaefer](https://github.com/uweschaefer))
- Issue \#889: limit reconnection attempts \(reset after a while\) [\#890](https://github.com/factcast/factcast/pull/890) ([uweschaefer](https://github.com/uweschaefer))
- \#884: Publish docker image during release [\#885](https://github.com/factcast/factcast/pull/885) ([otbe](https://github.com/otbe))
- Added ConditionalOnProperty for validation aspect [\#882](https://github.com/factcast/factcast/pull/882) ([otbe](https://github.com/otbe))

## [factcast-0.2.0](https://github.com/factcast/factcast/tree/factcast-0.2.0) (2020-06-21)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-RC2...factcast-0.2.0)

**Closed issues:**

- Publish Docker Image during release [\#884](https://github.com/factcast/factcast/issues/884)

## [factcast-0.2.0-RC2](https://github.com/factcast/factcast/tree/factcast-0.2.0-RC2) (2020-06-10)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-RC...factcast-0.2.0-RC2)

**Closed issues:**

- Factcast throws Exception \(on publish\) if validation is disabled [\#880](https://github.com/factcast/factcast/issues/880)
- replace LZ4 impl with commons-compress  [\#861](https://github.com/factcast/factcast/issues/861)

**Merged pull requests:**


## [factcast-0.2.0-RC](https://github.com/factcast/factcast/tree/factcast-0.2.0-RC) (2020-06-01)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M10...factcast-0.2.0-RC)

**Fixed bugs:**

- Ratrace in LISTENing for database notifications [\#780](https://github.com/factcast/factcast/issues/780)
- \#780: fixed pglistener to poll initially \*after\* reconnect [\#785](https://github.com/factcast/factcast/pull/785) ([uweschaefer](https://github.com/uweschaefer))

**Closed issues:**

- Fix usage of Nashorn \(for now\) in postQueryMatching [\#848](https://github.com/factcast/factcast/issues/848)
- Prepare for building with JDK\>8 [\#841](https://github.com/factcast/factcast/issues/841)
- reduce codacy violations to 0 [\#834](https://github.com/factcast/factcast/issues/834)
- ship lz4 codec with factcast server [\#832](https://github.com/factcast/factcast/issues/832)
- cleanup inspired by to IDEA warnings [\#830](https://github.com/factcast/factcast/issues/830)
- Update integration tests to use new API for transformation [\#828](https://github.com/factcast/factcast/issues/828)
- Restore / extend fetchById [\#804](https://github.com/factcast/factcast/issues/804)
- clear documentation regarding header field aggId\(s\) [\#788](https://github.com/factcast/factcast/issues/788)

**Merged pull requests:**

- \#848: fix nashorn engine [\#849](https://github.com/factcast/factcast/pull/849) ([uweschaefer](https://github.com/uweschaefer))
- \#841: Prepare for building with JDK\>8  [\#842](https://github.com/factcast/factcast/pull/842) ([uweschaefer](https://github.com/uweschaefer))
- \#834: codacy issues [\#835](https://github.com/factcast/factcast/pull/835) ([uweschaefer](https://github.com/uweschaefer))
- \#832: add lz4 & snappy dependency to server [\#833](https://github.com/factcast/factcast/pull/833) ([uweschaefer](https://github.com/uweschaefer))
- some minor cleanup [\#831](https://github.com/factcast/factcast/pull/831) ([uweschaefer](https://github.com/uweschaefer))
- isssue828: fixed minor bug for version==0, updated tests to use fetching i… [\#829](https://github.com/factcast/factcast/pull/829) ([uweschaefer](https://github.com/uweschaefer))
- WIP: Issue804 fetchById / fetchByIdAndVersion [\#824](https://github.com/factcast/factcast/pull/824) ([uweschaefer](https://github.com/uweschaefer))
- \#788: documentation clarification [\#789](https://github.com/factcast/factcast/pull/789) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.2.0-M10](https://github.com/factcast/factcast/tree/factcast-0.2.0-M10) (2020-04-07)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M9...factcast-0.2.0-M10)

**Closed issues:**

- stop registry cli from exiting when exitCode would be 0 [\#751](https://github.com/factcast/factcast/issues/751)
- remove lombok from kotlin modules [\#732](https://github.com/factcast/factcast/issues/732)
- Subscription without type does not return expected results [\#731](https://github.com/factcast/factcast/issues/731)
- Remove unused Feature: ID-Subscriptions [\#725](https://github.com/factcast/factcast/issues/725)
- Transformations [\#715](https://github.com/factcast/factcast/issues/715)
- SchemaStore needs to depend on SpringLiquibase [\#712](https://github.com/factcast/factcast/issues/712)
- Cleanup Security configuration [\#704](https://github.com/factcast/factcast/issues/704)
- Wrap Schemaregistry cli into maven plugin [\#660](https://github.com/factcast/factcast/issues/660)
- Integration Test [\#447](https://github.com/factcast/factcast/issues/447)

**Merged pull requests:**

- Issue751 prevent exit 0 [\#752](https://github.com/factcast/factcast/pull/752) ([uweschaefer](https://github.com/uweschaefer))
- Issue660 Maven Wrapper for Schema Registry CLI [\#749](https://github.com/factcast/factcast/pull/749) ([uweschaefer](https://github.com/uweschaefer))
- \#732: moved lombok to modules [\#743](https://github.com/factcast/factcast/pull/743) ([uweschaefer](https://github.com/uweschaefer))
- \#740: removed ancient dependency [\#742](https://github.com/factcast/factcast/pull/742) ([uweschaefer](https://github.com/uweschaefer))
- \#725: removed id subscriptions [\#726](https://github.com/factcast/factcast/pull/726) ([uweschaefer](https://github.com/uweschaefer))
- Issue715 [\#719](https://github.com/factcast/factcast/pull/719) ([uweschaefer](https://github.com/uweschaefer))
- \#712: declared dependency on liquibase [\#714](https://github.com/factcast/factcast/pull/714) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.2.0-M9](https://github.com/factcast/factcast/tree/factcast-0.2.0-M9) (2020-03-05)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M8...factcast-0.2.0-M9)

**Closed issues:**

- FactValidatorConfiguration is not autoconfigured [\#700](https://github.com/factcast/factcast/issues/700)

**Merged pull requests:**

- Add autoconfiguration for FactValidatorConfiguration [\#701](https://github.com/factcast/factcast/pull/701) ([otbe](https://github.com/otbe))

## [factcast-0.2.0-M8](https://github.com/factcast/factcast/tree/factcast-0.2.0-M8) (2020-03-04)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M7...factcast-0.2.0-M8)

**Closed issues:**

- Client yields "UNAUTHENTICATED: Authentication failed" even if Factcast security is disabled [\#696](https://github.com/factcast/factcast/issues/696)

**Merged pull requests:**

- Fixed non-matching passwords for unauthenticated access [\#697](https://github.com/factcast/factcast/pull/697) ([otbe](https://github.com/otbe))

## [factcast-0.2.0-M7](https://github.com/factcast/factcast/tree/factcast-0.2.0-M7) (2020-03-04)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M6...factcast-0.2.0-M7)

## [factcast-0.2.0-M6](https://github.com/factcast/factcast/tree/factcast-0.2.0-M6) (2020-03-03)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M5...factcast-0.2.0-M6)

## [factcast-0.2.0-M5](https://github.com/factcast/factcast/tree/factcast-0.2.0-M5) (2020-03-03)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M4...factcast-0.2.0-M5)

## [factcast-0.2.0-M4](https://github.com/factcast/factcast/tree/factcast-0.2.0-M4) (2020-03-03)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M3...factcast-0.2.0-M4)

## [factcast-0.2.0-M3](https://github.com/factcast/factcast/tree/factcast-0.2.0-M3) (2020-03-03)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.2.0-M2...factcast-0.2.0-M3)

**Closed issues:**

- Split secrets out of factcast-access.json [\#677](https://github.com/factcast/factcast/issues/677)

## [factcast-0.2.0-M2](https://github.com/factcast/factcast/tree/factcast-0.2.0-M2) (2020-03-03)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.4.2...factcast-0.2.0-M2)

**Implemented enhancements:**

- Send version information when publishing \(optional\) in order to be able to validate against a schema [\#599](https://github.com/factcast/factcast/issues/599)
- Schemaregistry that holds a catalog of schemas in order to validate on publish [\#598](https://github.com/factcast/factcast/issues/598)

**Fixed bugs:**

- DDL and DML user separation doesn't work as advertised [\#548](https://github.com/factcast/factcast/issues/548)

**Closed issues:**

- Fix Kotlin formatting [\#691](https://github.com/factcast/factcast/issues/691)
- fc-schema-cli does not compile on java 11 [\#688](https://github.com/factcast/factcast/issues/688)
- disable shading of jackson into core [\#657](https://github.com/factcast/factcast/issues/657)
- Factcast CLI fails on publishing events [\#643](https://github.com/factcast/factcast/issues/643)
- Add sponsoring note [\#639](https://github.com/factcast/factcast/issues/639)
- Flaky test PgQueryTest.testRoundtripCatchupEventsInsertedAfterStart [\#628](https://github.com/factcast/factcast/issues/628)
- Implement Schemaregistry configuration [\#627](https://github.com/factcast/factcast/issues/627)
- Flaky Test PgQueryTest.testRoundtripCatchupEventsInsertedAfterStart:164  [\#625](https://github.com/factcast/factcast/issues/625)
- Module for reference server docker building [\#621](https://github.com/factcast/factcast/issues/621)
- Remove the need for two maven profiles for spotless [\#618](https://github.com/factcast/factcast/issues/618)
- Make site generation platform agnostic [\#614](https://github.com/factcast/factcast/issues/614)
- Module for composite docker container with a factcast server & postgres embedded [\#610](https://github.com/factcast/factcast/issues/610)
- Wrap site generation in docker container [\#608](https://github.com/factcast/factcast/issues/608)
- Drop inMem impl of FactStore  [\#607](https://github.com/factcast/factcast/issues/607)
- Drop support for Spring-boot 1 [\#605](https://github.com/factcast/factcast/issues/605)
- Extend Security configuration to assign roles to namespaces [\#604](https://github.com/factcast/factcast/issues/604)
- Remove DDL/DML distinction as it causes more trouble than it is worth [\#601](https://github.com/factcast/factcast/issues/601)
- Jackson: CVE-2019-14379, CVE-2019-14439  [\#516](https://github.com/factcast/factcast/issues/516)
- Improve developer experience for eclipse users [\#510](https://github.com/factcast/factcast/issues/510)
- configure maven plugin version explicitly [\#507](https://github.com/factcast/factcast/issues/507)
- use -RAM instance for postgres in circle-CI [\#505](https://github.com/factcast/factcast/issues/505)
- Bump spring-grpc.version from 2.3.0.RELEASE to 2.4.0.RELEASE [\#445](https://github.com/factcast/factcast/issues/445)

**Merged pull requests:**

- build\(deps\): bump jackson-databind from 2.9.10.1 to 2.9.10.3 in /factcast-bom [\#698](https://github.com/factcast/factcast/pull/698) ([dependabot[bot]](https://github.com/apps/dependabot))
- \#691: updated ktlint dep [\#692](https://github.com/factcast/factcast/pull/692) ([uweschaefer](https://github.com/uweschaefer))
- added javax.annotation dependency for Java11 compatibility [\#690](https://github.com/factcast/factcast/pull/690) ([uweschaefer](https://github.com/uweschaefer))
- Extend Security configuration to assign roles to namespaces [\#672](https://github.com/factcast/factcast/pull/672) ([uweschaefer](https://github.com/uweschaefer))
- lombok\_nullcheck\_config [\#670](https://github.com/factcast/factcast/pull/670) ([uweschaefer](https://github.com/uweschaefer))
- WIP \#598: Fixed packaging of hugo template [\#669](https://github.com/factcast/factcast/pull/669) ([otbe](https://github.com/otbe))
- \#661: updated jackson dependency [\#663](https://github.com/factcast/factcast/pull/663) ([uweschaefer](https://github.com/uweschaefer))
- Issue639 sponsoring logo [\#659](https://github.com/factcast/factcast/pull/659) ([uweschaefer](https://github.com/uweschaefer))
- \#657: stop shading jackson into core [\#658](https://github.com/factcast/factcast/pull/658) ([uweschaefer](https://github.com/uweschaefer))
- Let the hugo container create and serve files with host users groupid [\#653](https://github.com/factcast/factcast/pull/653) ([mweirauch](https://github.com/mweirauch))
- Adds CLI tool for Schema Registry - \#598 [\#649](https://github.com/factcast/factcast/pull/649) ([otbe](https://github.com/otbe))
- Issue643 [\#646](https://github.com/factcast/factcast/pull/646) ([uweschaefer](https://github.com/uweschaefer))
- \#627: crawl schemaregistry and validate on publish [\#640](https://github.com/factcast/factcast/pull/640) ([uweschaefer](https://github.com/uweschaefer))
- \#628 flaky test [\#629](https://github.com/factcast/factcast/pull/629) ([uweschaefer](https://github.com/uweschaefer))
- \#625: fix flaky test [\#626](https://github.com/factcast/factcast/pull/626) ([uweschaefer](https://github.com/uweschaefer))
- \#612 add integration test via grpc [\#623](https://github.com/factcast/factcast/pull/623) ([uweschaefer](https://github.com/uweschaefer))
- \#621: docker container  [\#622](https://github.com/factcast/factcast/pull/622) ([uweschaefer](https://github.com/uweschaefer))
- \#614 remove hugo binaries [\#620](https://github.com/factcast/factcast/pull/620) ([uweschaefer](https://github.com/uweschaefer))
- \#618: simplified spotless config [\#619](https://github.com/factcast/factcast/pull/619) ([uweschaefer](https://github.com/uweschaefer))
- Issue614 removed hugo binaries [\#617](https://github.com/factcast/factcast/pull/617) ([uweschaefer](https://github.com/uweschaefer))
- \#607: removed inmem module [\#609](https://github.com/factcast/factcast/pull/609) ([uweschaefer](https://github.com/uweschaefer))
- \#605: removed spring1 client example [\#606](https://github.com/factcast/factcast/pull/606) ([uweschaefer](https://github.com/uweschaefer))
- WIP \#601: removed dml user mechanism [\#603](https://github.com/factcast/factcast/pull/603) ([uweschaefer](https://github.com/uweschaefer))
- build\(deps\): bump jackson-databind from 2.9.9.2 to 2.9.10.1 in /factcast-bom [\#597](https://github.com/factcast/factcast/pull/597) ([dependabot[bot]](https://github.com/apps/dependabot))
- Update \_index.md [\#577](https://github.com/factcast/factcast/pull/577) ([Eszti](https://github.com/Eszti))
- upgrade jackson-databind to 2.9.9.2 [\#517](https://github.com/factcast/factcast/pull/517) ([uweschaefer](https://github.com/uweschaefer))
- configured os maven plugin [\#511](https://github.com/factcast/factcast/pull/511) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.1.4.2](https://github.com/factcast/factcast/tree/factcast-0.1.4.2) (2019-07-28)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.4.1...factcast-0.1.4.2)

**Closed issues:**

- Detach from mercateo oss parent pom [\#503](https://github.com/factcast/factcast/issues/503)

## [factcast-0.1.4.1](https://github.com/factcast/factcast/tree/factcast-0.1.4.1) (2019-07-28)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.4...factcast-0.1.4.1)

**Fixed bugs:**

- 0.1.4 contains SLF4J-API classes [\#496](https://github.com/factcast/factcast/issues/496)

**Closed issues:**


**Merged pull requests:**

- pom cleanup [\#504](https://github.com/factcast/factcast/pull/504) ([uweschaefer](https://github.com/uweschaefer))
- codacy suggestion [\#502](https://github.com/factcast/factcast/pull/502) ([uweschaefer](https://github.com/uweschaefer))
- set jackson databind to 2.9.9.1 [\#501](https://github.com/factcast/factcast/pull/501) ([uweschaefer](https://github.com/uweschaefer))
- \#496: exclude accidentally shaded artifacts [\#497](https://github.com/factcast/factcast/pull/497) ([uweschaefer](https://github.com/uweschaefer))
- Junit 1.5.1 [\#495](https://github.com/factcast/factcast/pull/495) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.1.4](https://github.com/factcast/factcast/tree/factcast-0.1.4) (2019-07-20)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.3...factcast-0.1.4)

## [factcast-0.1.3](https://github.com/factcast/factcast/tree/factcast-0.1.3) (2019-07-20)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.2...factcast-0.1.3)

## [factcast-0.1.2](https://github.com/factcast/factcast/tree/factcast-0.1.2) (2019-07-20)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.0...factcast-0.1.2)

**Closed issues:**

- Jackson Databind Security fix [\#489](https://github.com/factcast/factcast/issues/489)
- pgsql-rds: TomcatJdbcDataSourceFactory properties \(spring.datasource.tomcat.\*\) are not regarded [\#485](https://github.com/factcast/factcast/issues/485)
- Shade jackson [\#470](https://github.com/factcast/factcast/issues/470)

**Merged pull requests:**

- jackson databind CVE -\> 2.9.9.1 [\#490](https://github.com/factcast/factcast/pull/490) ([uweschaefer](https://github.com/uweschaefer))
- Issue485 [\#487](https://github.com/factcast/factcast/pull/487) ([uweschaefer](https://github.com/uweschaefer))
- Set higher priority for RdsDataSourceFactoryBeanPostProcessor [\#486](https://github.com/factcast/factcast/pull/486) ([C-Otto](https://github.com/C-Otto))
- codacy improvement [\#484](https://github.com/factcast/factcast/pull/484) ([uweschaefer](https://github.com/uweschaefer))
- codacy improvement [\#483](https://github.com/factcast/factcast/pull/483) ([uweschaefer](https://github.com/uweschaefer))
- codacy improvement [\#482](https://github.com/factcast/factcast/pull/482) ([uweschaefer](https://github.com/uweschaefer))
- relocate jackson [\#481](https://github.com/factcast/factcast/pull/481) ([uweschaefer](https://github.com/uweschaefer))
- Drop corporate Maven profile. [\#476](https://github.com/factcast/factcast/pull/476) ([mweirauch](https://github.com/mweirauch))
- Don't deploy examples, site and internals [\#475](https://github.com/factcast/factcast/pull/475) ([mweirauch](https://github.com/mweirauch))
- Update ExceptionAfterPublish.java [\#465](https://github.com/factcast/factcast/pull/465) ([uweschaefer](https://github.com/uweschaefer))

## [factcast-0.1.0](https://github.com/factcast/factcast/tree/factcast-0.1.0) (2019-06-24)

[Full Changelog](https://github.com/factcast/factcast/compare/factcast-0.1.0-RC4...factcast-0.1.0)

**Implemented enhancements:**

- Fix initialization order of PG-Components [\#441](https://github.com/factcast/factcast/issues/441)
- Unify autoconfiguration [\#310](https://github.com/factcast/factcast/issues/310)
- Extend API to enumerate existing namespaces & types in store [\#153](https://github.com/factcast/factcast/issues/153)
- Provide CLI Client for GRPC  [\#152](https://github.com/factcast/factcast/issues/152)
- Provide Builder for Fact to make sure it has a namespace [\#147](https://github.com/factcast/factcast/issues/147)
- Add TLS for communication encryption [\#122](https://github.com/factcast/factcast/issues/122)
- gRPC: add Codec for snappy or lz4 compression [\#77](https://github.com/factcast/factcast/issues/77)
- Prepare for Compaction [\#60](https://github.com/factcast/factcast/issues/60)
- Add Channel Authentication [\#55](https://github.com/factcast/factcast/issues/55)
- Question about MarkFacts & sync UIs [\#44](https://github.com/factcast/factcast/issues/44)
- Performance: Use Queue for Paging in Catchup process [\#38](https://github.com/factcast/factcast/issues/38)
- Enable GZIP for REST-API [\#27](https://github.com/factcast/factcast/issues/27)
- Try to change LISTEN strategy, so that traditional JDBC Driver can be used. [\#26](https://github.com/factcast/factcast/issues/26)
- Introduce faster streams for REST and Document client setup  [\#23](https://github.com/factcast/factcast/issues/23)
- Add proper javadocs [\#22](https://github.com/factcast/factcast/issues/22)

**Fixed bugs:**

- grpc client version 36 incompatible to master [\#432](https://github.com/factcast/factcast/issues/432)
- Unexpected BuildError in circleCI that is not locally repoducable [\#430](https://github.com/factcast/factcast/issues/430)
- dynamic AWS RDS-Settings doesn't work with version 0.1.0-M1 [\#408](https://github.com/factcast/factcast/issues/408)
- InMem FactCast doesn't support lastKnownEventId [\#307](https://github.com/factcast/factcast/issues/307)
- Fact.Builder needs public methods [\#165](https://github.com/factcast/factcast/issues/165)
- Facts with same id are not reported as duplicate to grpc client [\#149](https://github.com/factcast/factcast/issues/149)
- Update compilation problem with Java10 [\#139](https://github.com/factcast/factcast/issues/139)
- Bug: GRPC API Accepts publishing of Facts without namespace [\#137](https://github.com/factcast/factcast/issues/137)
- \[emergency\] RDS autoConfig throws NPE [\#135](https://github.com/factcast/factcast/issues/135)
- Implement serialOf in grpc server [\#133](https://github.com/factcast/factcast/issues/133)
- GRPC Service RemoteFactCast is not auto-configured [\#124](https://github.com/factcast/factcast/issues/124)
- Flaky test setup with circleCI [\#59](https://github.com/factcast/factcast/issues/59)
- Follow subscriptions no longer work after "Connection to \[url\] refused". [\#56](https://github.com/factcast/factcast/issues/56)
- flaky test [\#49](https://github.com/factcast/factcast/issues/49)
- application.properties in server project with datasource parameters [\#42](https://github.com/factcast/factcast/issues/42)
- Catchup: remove need for long running JDBC Connection [\#30](https://github.com/factcast/factcast/issues/30)
- Renaming continous to continuous [\#28](https://github.com/factcast/factcast/issues/28)
- Connection lost while subscribing [\#25](https://github.com/factcast/factcast/issues/25)

**Closed issues:**

- added currentTimestamp\(\) to store interface [\#459](https://github.com/factcast/factcast/issues/459)
- Extend GRPC Protocol with a call to fetch the current database-time [\#458](https://github.com/factcast/factcast/issues/458)
- Abstract filterScript to enable future extension with non-js scripts. [\#457](https://github.com/factcast/factcast/issues/457)
- Security: jackson databind vulnerability  [\#456](https://github.com/factcast/factcast/issues/456)
- Bump spring-boot-dependencies from 2.1.5.RELEASE to 2.1.6.RELEASE [\#455](https://github.com/factcast/factcast/issues/455)
- Bump postgresql from 42.2.5 to 42.2.6 [\#454](https://github.com/factcast/factcast/issues/454)
- Bump spring-boot-maven-plugin from 2.1.5.RELEASE to 2.1.6.RELEASE [\#452](https://github.com/factcast/factcast/issues/452)
- Bump spotless-maven-plugin from 1.23.0 to 1.23.1 [\#451](https://github.com/factcast/factcast/issues/451)
- Bump guava from 27.1-jre to 28.0-jre [\#449](https://github.com/factcast/factcast/issues/449)
- log as info. no need to log error within a working feature [\#448](https://github.com/factcast/factcast/issues/448)
- fixed grpc.client properties in examples and doc [\#446](https://github.com/factcast/factcast/issues/446)
- Bump mockito.version from 2.27.0 to 2.28.2 [\#444](https://github.com/factcast/factcast/issues/444)
- Abstract javascript in subReq in order to be open to extensions [\#443](https://github.com/factcast/factcast/issues/443)
- \#441: serialized init order [\#442](https://github.com/factcast/factcast/issues/442)
- Bump grpc.version from 1.18.0 to 1.21.0 [\#440](https://github.com/factcast/factcast/issues/440)
- Bump postgresql from 1.11.2 to 1.11.3 [\#439](https://github.com/factcast/factcast/issues/439)
- PgSQL test setup polish [\#438](https://github.com/factcast/factcast/issues/438)
- \#428: cli usage page [\#437](https://github.com/factcast/factcast/issues/437)
- Issue432 JSON Deser problem on unknown properties [\#436](https://github.com/factcast/factcast/issues/436)
- Bump oss-parent-pom from 1.0.7 to 1.0.9 [\#435](https://github.com/factcast/factcast/issues/435)
- \#430: flaky test [\#434](https://github.com/factcast/factcast/issues/434)
- Move and rename PgSqlListenerTest.java [\#433](https://github.com/factcast/factcast/issues/433)
- \#430: build error - hanging test [\#431](https://github.com/factcast/factcast/issues/431)
- Update XML formatter version [\#429](https://github.com/factcast/factcast/issues/429)
- Document CLI [\#428](https://github.com/factcast/factcast/issues/428)
- \#424: fixed host/port -\> address, added --basic [\#427](https://github.com/factcast/factcast/issues/427)
- \#425: added xml formatting config and reformatted accordingly [\#426](https://github.com/factcast/factcast/issues/426)
- Align spotless formatting rules for xml with maven's [\#425](https://github.com/factcast/factcast/issues/425)
- Add BasicAuth capablity to CLI [\#424](https://github.com/factcast/factcast/issues/424)
- \#70: repeatedly check listen/notify on pgconnection [\#423](https://github.com/factcast/factcast/issues/423)
- \#407: added basicauth docs, reorganized menu [\#422](https://github.com/factcast/factcast/issues/422)
- Issue\#272 document good settings for keepalive [\#421](https://github.com/factcast/factcast/issues/421)
- Fix Spring Boot 1 example client dependency [\#420](https://github.com/factcast/factcast/issues/420)
- Bump spring-boot-maven-plugin from 2.1.4.RELEASE to 2.1.5.RELEASE [\#419](https://github.com/factcast/factcast/issues/419)
- Bump spring-boot-starter-security from 2.1.4.RELEASE to 2.1.5.RELEASE [\#418](https://github.com/factcast/factcast/issues/418)
- Bump spring-boot-dependencies from 2.1.4.RELEASE to 2.1.5.RELEASE [\#417](https://github.com/factcast/factcast/issues/417)
- Bump jacoco-maven-plugin from 0.8.3 to 0.8.4 [\#416](https://github.com/factcast/factcast/issues/416)
- Update Spotless Eclipse Formatter to 4.11 [\#415](https://github.com/factcast/factcast/issues/415)
- pgsql: add micrometer metrics [\#414](https://github.com/factcast/factcast/issues/414)
- Issue412 remove subscribeToIds etc [\#413](https://github.com/factcast/factcast/issues/413)
- Remove caching & id-subscription [\#412](https://github.com/factcast/factcast/issues/412)
- issue 408: [\#411](https://github.com/factcast/factcast/issues/411)
- Bump lombok from 1.18.6 to 1.18.8 [\#410](https://github.com/factcast/factcast/issues/410)
- \#404: automatic reconnection in daemon thread [\#409](https://github.com/factcast/factcast/issues/409)
- Document Basic-Auth setup [\#407](https://github.com/factcast/factcast/issues/407)
- Issue405 site update [\#406](https://github.com/factcast/factcast/issues/406)
- Update site [\#405](https://github.com/factcast/factcast/issues/405)
- Change subscription follow behaviour to reconnecting [\#404](https://github.com/factcast/factcast/issues/404)
- \#212: moved property namespace [\#403](https://github.com/factcast/factcast/issues/403)
- \#401: guard against null returned from attempt\(\) [\#402](https://github.com/factcast/factcast/issues/402)
- Opt.Locking: Check against null returned from attempt\(\) [\#401](https://github.com/factcast/factcast/issues/401)
- Bump spotless-maven-plugin from 1.22.0 to 1.23.0 [\#400](https://github.com/factcast/factcast/issues/400)
- \#397: removed markfacts from docs [\#399](https://github.com/factcast/factcast/issues/399)
- Issue390 [\#398](https://github.com/factcast/factcast/issues/398)
- Remove MarkFacts from documentation [\#397](https://github.com/factcast/factcast/issues/397)
- WIP Issue395 [\#396](https://github.com/factcast/factcast/issues/396)
- Apply IDEA inspection suggestions [\#395](https://github.com/factcast/factcast/issues/395)
- \#251: removed marks [\#394](https://github.com/factcast/factcast/issues/394)
- \#210: removed metrics [\#393](https://github.com/factcast/factcast/issues/393)
- Add proper Metrics using micrometer.io [\#392](https://github.com/factcast/factcast/issues/392)
- \#390: added snappy [\#391](https://github.com/factcast/factcast/issues/391)
- Add Snappy compression Codec [\#390](https://github.com/factcast/factcast/issues/390)
- Bump spring-boot-starter-security from 2.1.3.RELEASE to 2.1.4.RELEASE [\#389](https://github.com/factcast/factcast/issues/389)
- postgres module uuid-oosp documentation [\#388](https://github.com/factcast/factcast/issues/388)
- Bump spotless-maven-plugin from 1.21.1 to 1.22.0 [\#387](https://github.com/factcast/factcast/issues/387)
- Bump postgresql from 1.11.1 to 1.11.2 [\#386](https://github.com/factcast/factcast/issues/386)
- \#140 lz4codec [\#385](https://github.com/factcast/factcast/issues/385)
- \#250-aggregate-validation-errors: aggregate validation errors [\#384](https://github.com/factcast/factcast/issues/384)
- \#382: oneliners [\#383](https://github.com/factcast/factcast/issues/383)
- Cleanup all README.md [\#382](https://github.com/factcast/factcast/issues/382)
- \#234-gzip-compression-reenable: reenable gzip message compression [\#381](https://github.com/factcast/factcast/issues/381)
- Refactor GrpcFactStore.java [\#380](https://github.com/factcast/factcast/issues/380)
- \#370-macosx-hugo: script dispatching to respective binary [\#379](https://github.com/factcast/factcast/issues/379)
- \#55-basicauth: added basicauth [\#378](https://github.com/factcast/factcast/issues/378)
- Bump netty-tcnative-boringssl-static from 2.0.24.Final to 2.0.25.Final [\#377](https://github.com/factcast/factcast/issues/377)
- pass along command line arguments to SpringApplication [\#376](https://github.com/factcast/factcast/issues/376)
- Bump mockito.version from 2.26.0 to 2.27.0 [\#375](https://github.com/factcast/factcast/issues/375)
- \#373: hacky way to work with either version 2.1 and 2.3 of grpc-sprin… [\#374](https://github.com/factcast/factcast/issues/374)
- Fix Spring Boot1 example dependencies [\#373](https://github.com/factcast/factcast/issues/373)
- Bump grpc.version from 1.18.0 to 1.20.0 [\#371](https://github.com/factcast/factcast/issues/371)
- Scripts starting Hugo should use a macos version on macos  [\#370](https://github.com/factcast/factcast/issues/370)
- Spotless polish [\#368](https://github.com/factcast/factcast/issues/368)
- Bump junit-platform-commons from 1.4.1 to 1.4.2 [\#367](https://github.com/factcast/factcast/issues/367)
- \#issue353 :  minor change : fixed typo in bash script output only [\#366](https://github.com/factcast/factcast/issues/366)
- Issue364 upgrade to junit5 - coverage problem [\#365](https://github.com/factcast/factcast/issues/365)
- Evaluate Coverage drop when updating to junit 5.4 [\#364](https://github.com/factcast/factcast/issues/364)
- Issue332 Optimistic Locking Documentation [\#363](https://github.com/factcast/factcast/issues/363)
- Bump junit-platform-engine from 1.4.0 to 1.4.2 [\#362](https://github.com/factcast/factcast/issues/362)
- Bump junit-jupiter-engine from 5.3.2 to 5.4.2 [\#361](https://github.com/factcast/factcast/issues/361)
- Bump spring-boot-maven-plugin from 2.1.3.RELEASE to 2.1.4.RELEASE [\#360](https://github.com/factcast/factcast/issues/360)
- Bump junit-jupiter-api from 5.3.2 to 5.4.2 [\#359](https://github.com/factcast/factcast/issues/359)
- Bump mockito.version from 2.25.1 to 2.26.0 [\#358](https://github.com/factcast/factcast/issues/358)
- Bump spring-boot-dependencies from 2.1.3.RELEASE to 2.1.4.RELEASE [\#357](https://github.com/factcast/factcast/issues/357)
- \#355: Updated spring-grpc version [\#356](https://github.com/factcast/factcast/issues/356)
- Bump spring-grpc.version from 2.1.0.RELEASE to 2.3.0.RELEASE [\#355](https://github.com/factcast/factcast/issues/355)
- Issue353 spotless auto-apply  [\#354](https://github.com/factcast/factcast/issues/354)
- Add configuration option for spotless auto-apply [\#353](https://github.com/factcast/factcast/issues/353)
- Issue351 fix maven plugin version warnings [\#352](https://github.com/factcast/factcast/issues/352)
- fix version warnings for spring-maven-plugin  [\#351](https://github.com/factcast/factcast/issues/351)
- Issue189 spotless [\#350](https://github.com/factcast/factcast/issues/350)
- Issue348 update to latest version of yidongnan/grpc-spring-boot-starter [\#349](https://github.com/factcast/factcast/issues/349)
- Update to latest grpc/netty deps [\#348](https://github.com/factcast/factcast/issues/348)
- removed unique\_identifier feature [\#347](https://github.com/factcast/factcast/issues/347)
- Remove unique\_identifier [\#346](https://github.com/factcast/factcast/issues/346)
- Issue239 factstore extensions [\#345](https://github.com/factcast/factcast/issues/345)
- Issue338 transaction scoped locks [\#344](https://github.com/factcast/factcast/issues/344)
- \#341: add lockGlobally [\#343](https://github.com/factcast/factcast/issues/343)
- Issue331 - implement optimistic locking via GRPC [\#342](https://github.com/factcast/factcast/issues/342)
- Subtask to \#325: allow for state capturing independent of namespace [\#341](https://github.com/factcast/factcast/issues/341)
- Fix maven plugin configuration [\#340](https://github.com/factcast/factcast/issues/340)
- improved usage of xmllint [\#339](https://github.com/factcast/factcast/issues/339)
- Missing events due to race-condition during concurrent fact-publish [\#338](https://github.com/factcast/factcast/issues/338)
- add test to reproduce missing events in subscription [\#337](https://github.com/factcast/factcast/issues/337)
- Bump netty-tcnative-boringssl-static from 2.0.17.Final to 2.0.24.Final [\#336](https://github.com/factcast/factcast/issues/336)
- Issue330: Implement optimistic locking aspect of PgFactStore [\#335](https://github.com/factcast/factcast/issues/335)
- Issue330 [\#334](https://github.com/factcast/factcast/issues/334)
- Issue329 PgTokenStore [\#333](https://github.com/factcast/factcast/issues/333)
- Subtask to \#325: Document optimistic locking [\#332](https://github.com/factcast/factcast/issues/332)
- Subtask to \#325: Extend GRPC protocol to support new methods on FactStore [\#331](https://github.com/factcast/factcast/issues/331)
- Subtask to \#325: Implement methods used by optimistic locking on PgFactStore [\#330](https://github.com/factcast/factcast/issues/330)
- Subtask to \#325: Implement Pg-based TokenStore [\#329](https://github.com/factcast/factcast/issues/329)
- Bump postgresql from 1.11.0 to 1.11.1 [\#328](https://github.com/factcast/factcast/issues/328)
- Bump postgresql from 1.10.7 to 1.11.0 [\#327](https://github.com/factcast/factcast/issues/327)
- Bump netty-tcnative-boringssl-static from 2.0.17.Final to 2.0.23.Final [\#326](https://github.com/factcast/factcast/issues/326)
- Optimistic locking [\#325](https://github.com/factcast/factcast/issues/325)
- Issue312 fix documentation regarding project setup & examples [\#324](https://github.com/factcast/factcast/issues/324)
- fix: class renamed, but not spring.factories [\#323](https://github.com/factcast/factcast/issues/323)
- Bump junit-jupiter-engine from 5.3.2 to 5.4.1 [\#322](https://github.com/factcast/factcast/issues/322)
- Bump junit-jupiter-api from 5.3.2 to 5.4.1 [\#321](https://github.com/factcast/factcast/issues/321)
- Bump mockito.version from 2.25.0 to 2.25.1 [\#320](https://github.com/factcast/factcast/issues/320)
- Bump junit-platform-engine from 1.4.0 to 1.4.1 [\#319](https://github.com/factcast/factcast/issues/319)
- Bump assertj-core from 3.12.1 to 3.12.2 [\#318](https://github.com/factcast/factcast/issues/318)
- Remove rest from site [\#317](https://github.com/factcast/factcast/issues/317)
- Cleanup pg prefixes on classnames depends on pr315 [\#316](https://github.com/factcast/factcast/issues/316)
- Issue310 autoconfiguration [\#315](https://github.com/factcast/factcast/issues/315)
- Bump postgresql from 1.10.6 to 1.10.7 [\#313](https://github.com/factcast/factcast/issues/313)
- Update the 'Server Setup' documentation [\#312](https://github.com/factcast/factcast/issues/312)
- Issue310 autoconfiguration [\#311](https://github.com/factcast/factcast/issues/311)
- Bump guava from 27.0.1-jre to 27.1-jre [\#309](https://github.com/factcast/factcast/issues/309)
- Fix catchup with startingFrom-FactId [\#308](https://github.com/factcast/factcast/issues/308)
- Bump mockito.version from 2.24.5 to 2.25.0 [\#306](https://github.com/factcast/factcast/issues/306)
- Bump spring-cloud-dependencies from Greenwich.RELEASE to Greenwich.SR1 [\#305](https://github.com/factcast/factcast/issues/305)
- Bump netty-tcnative-boringssl-static from 2.0.17.Final to 2.0.22.Final [\#304](https://github.com/factcast/factcast/issues/304)
- Bump assertj-core from 3.12.0 to 3.12.1 [\#303](https://github.com/factcast/factcast/issues/303)
- Bump grpc.version from 1.18.0 to 1.19.0 [\#302](https://github.com/factcast/factcast/issues/302)
- Update pom.xml [\#301](https://github.com/factcast/factcast/issues/301)
- Bump mockito.version from 2.24.0 to 2.24.5 [\#300](https://github.com/factcast/factcast/issues/300)
- Bump junit-jupiter-engine from 5.3.2 to 5.4.0 [\#299](https://github.com/factcast/factcast/issues/299)
- Bump slf4j-api.version from 1.7.25 to 1.7.26 [\#298](https://github.com/factcast/factcast/issues/298)
- Bump junit-jupiter-api from 5.3.2 to 5.4.0 [\#297](https://github.com/factcast/factcast/issues/297)
- Bump lombok from 1.18.4 to 1.18.6 [\#296](https://github.com/factcast/factcast/issues/296)
- Bump assertj-core from 3.11.1 to 3.12.0 [\#295](https://github.com/factcast/factcast/issues/295)
- Bump os-maven-plugin from 1.6.1 to 1.6.2 [\#294](https://github.com/factcast/factcast/issues/294)
- Bump spring-grpc.version from 2.1.0.RELEASE to 2.2.1.RELEASE [\#293](https://github.com/factcast/factcast/issues/293)
- Bump spring-boot-maven-plugin from 1.5.17.RELEASE to 2.1.3.RELEASE [\#292](https://github.com/factcast/factcast/issues/292)
- Bump spring-boot-dependencies from 2.1.1.RELEASE to 2.1.3.RELEASE [\#291](https://github.com/factcast/factcast/issues/291)
- Bump junit-platform-engine from 1.3.2 to 1.4.0 [\#290](https://github.com/factcast/factcast/issues/290)
- Bump mockito.version from 2.23.4 to 2.24.0 [\#289](https://github.com/factcast/factcast/issues/289)
- Bump liquibase-core from 3.6.2 to 3.6.3 [\#288](https://github.com/factcast/factcast/issues/288)
- Bump postgresql from 1.10.5 to 1.10.6 [\#287](https://github.com/factcast/factcast/issues/287)
- Bump jacoco-maven-plugin from 0.8.2 to 0.8.3 [\#286](https://github.com/factcast/factcast/issues/286)
- Bump spring-cloud-dependencies from Finchley.SR2 to Greenwich.RELEASE [\#285](https://github.com/factcast/factcast/issues/285)
- Bump grpc.version from 1.17.1 to 1.18.0 [\#284](https://github.com/factcast/factcast/issues/284)
- Bump spring-boot-maven-plugin from 1.5.17.RELEASE to 2.1.2.RELEASE [\#283](https://github.com/factcast/factcast/issues/283)
- Bump spring-boot-dependencies from 2.1.1.RELEASE to 2.1.2.RELEASE [\#282](https://github.com/factcast/factcast/issues/282)
- Bump oss-parent-pom from 1.0.6 to 1.0.7 [\#281](https://github.com/factcast/factcast/issues/281)
- Bump oss-parent-pom from 1.0.5 to 1.0.6 [\#280](https://github.com/factcast/factcast/issues/280)
- Bump postgresql from 1.10.4 to 1.10.5 [\#279](https://github.com/factcast/factcast/issues/279)
- Bump postgresql from 1.10.3 to 1.10.4 [\#278](https://github.com/factcast/factcast/issues/278)
- Bump maven-failsafe-plugin from 3.0.0-M2 to 3.0.0-M3 [\#277](https://github.com/factcast/factcast/issues/277)
- Bump maven-surefire-plugin from 3.0.0-M2 to 3.0.0-M3 [\#276](https://github.com/factcast/factcast/issues/276)
- Bump spring-grpc.version from 2.1.0.RELEASE to 2.2.0.RELEASE [\#275](https://github.com/factcast/factcast/issues/275)
- Bump postgresql from 1.10.2 to 1.10.3 [\#274](https://github.com/factcast/factcast/issues/274)
- Bump jackson-databind from 2.9.7 to 2.9.8 [\#273](https://github.com/factcast/factcast/issues/273)
- Document good settings for keepalive behaviour [\#272](https://github.com/factcast/factcast/issues/272)
- \#270: added --no-tls [\#271](https://github.com/factcast/factcast/issues/271)
- Add --no-tls to CLI to connect using plaintext [\#270](https://github.com/factcast/factcast/issues/270)
- print usage when cli options incomplete [\#269](https://github.com/factcast/factcast/issues/269)
- CLI: Exceptions when Options incomplete [\#268](https://github.com/factcast/factcast/issues/268)
- Bump maven-surefire-plugin from 3.0.0-M1 to 3.0.0-M2 [\#267](https://github.com/factcast/factcast/issues/267)
- Bump maven-failsafe-plugin from 3.0.0-M1 to 3.0.0-M2 [\#266](https://github.com/factcast/factcast/issues/266)
- \#264: removed queued catchup strategy [\#265](https://github.com/factcast/factcast/issues/265)
- Drop Queued Catchup [\#264](https://github.com/factcast/factcast/issues/264)
- Issue260 [\#263](https://github.com/factcast/factcast/issues/263)
- Bump grpc.version from 1.16.1 to 1.17.0 [\#262](https://github.com/factcast/factcast/issues/262)
- Fix scope of test dependencies and some order shuffling. [\#261](https://github.com/factcast/factcast/issues/261)
- Create abstraction for Retryable Exceptions between Store and FactCast [\#260](https://github.com/factcast/factcast/issues/260)
- Bump spring-boot-dependencies from 2.1.0.RELEASE to 2.1.1.RELEASE [\#259](https://github.com/factcast/factcast/issues/259)
- Bump spring-boot-maven-plugin from 1.5.17.RELEASE to 2.1.1.RELEASE [\#258](https://github.com/factcast/factcast/issues/258)
- Bump postgresql from 1.10.1 to 1.10.2 [\#257](https://github.com/factcast/factcast/issues/257)
- Bump junit-jupiter-api from 5.3.1 to 5.3.2 [\#256](https://github.com/factcast/factcast/issues/256)
- Bump junit-platform-engine from 1.3.1 to 1.3.2 [\#255](https://github.com/factcast/factcast/issues/255)
- Bump netty-tcnative-boringssl-static from 2.0.17.Final to 2.0.20.Final [\#254](https://github.com/factcast/factcast/issues/254)
- Bump junit-jupiter-engine from 5.3.1 to 5.3.2 [\#253](https://github.com/factcast/factcast/issues/253)
- Issue247 [\#252](https://github.com/factcast/factcast/issues/252)
- Better validation errors on publishing [\#250](https://github.com/factcast/factcast/issues/250)
- \#246: more careful catchup mechanics [\#249](https://github.com/factcast/factcast/issues/249)
- this should break the build due to formal incorrectnes [\#248](https://github.com/factcast/factcast/issues/248)
- Check formal requirements on CI [\#247](https://github.com/factcast/factcast/issues/247)
- Flaky test in InMemStore  [\#246](https://github.com/factcast/factcast/issues/246)
- CLI: add switch to talk to TLS [\#245](https://github.com/factcast/factcast/issues/245)
- Issue240 make test methods package private [\#244](https://github.com/factcast/factcast/issues/244)
- \#240\_coverage\_analysis: baseline [\#243](https://github.com/factcast/factcast/issues/243)
- Issue240 Rename TestCases and limit test method scopes \(JUnit5\) [\#242](https://github.com/factcast/factcast/issues/242)
- Issue122 [\#241](https://github.com/factcast/factcast/issues/241)
- Cleanup Tests [\#240](https://github.com/factcast/factcast/issues/240)
- Extend API with prerequisites for optimistic locking [\#239](https://github.com/factcast/factcast/issues/239)
- Issue122 TLS examples [\#238](https://github.com/factcast/factcast/issues/238)
- Bump mockito.version from 2.23.0 to 2.23.4 [\#237](https://github.com/factcast/factcast/issues/237)
- Issue235 copyright header migration from javadoc to slash-star [\#236](https://github.com/factcast/factcast/issues/236)
- Migrate copyright headers from javadoc to slashstar [\#235](https://github.com/factcast/factcast/issues/235)
- GRPC Client-\>Server communication regression [\#234](https://github.com/factcast/factcast/issues/234)
- Bump guava from 27.0-jre to 27.0.1-jre [\#233](https://github.com/factcast/factcast/issues/233)
- fixed empty catch block [\#232](https://github.com/factcast/factcast/issues/232)
- Bump spring-boot-maven-plugin from 1.5.17.RELEASE to 2.1.0.RELEASE [\#231](https://github.com/factcast/factcast/issues/231)
- Bump docker-maven-plugin from 0.4.9 to 1.2.0 [\#230](https://github.com/factcast/factcast/issues/230)
- Bump guava from 26.0-jre to 27.0-jre [\#229](https://github.com/factcast/factcast/issues/229)
- Bump slf4j-api from 1.7.22 to 1.7.25 [\#228](https://github.com/factcast/factcast/issues/228)
- Bump protobuf-maven-plugin from 0.5.1 to 0.6.1 [\#227](https://github.com/factcast/factcast/issues/227)
- Fix 'Empty catch block.' issue in factcast-server-grpc\src\main\java\org\factcast\server\grpc\FactStoreGrpcService.java [\#226](https://github.com/factcast/factcast/issues/226)
- Fix 'Empty catch block.' issue in factcast-server-grpc\src\main\java\org\factcast\server\grpc\FactStoreGrpcService.java [\#225](https://github.com/factcast/factcast/issues/225)
- Bump build-helper-maven-plugin from 1.8 to 3.0.0 [\#223](https://github.com/factcast/factcast/issues/223)
- Bump postgresql from 42.2.5.jre7 to 42.2.5 [\#222](https://github.com/factcast/factcast/issues/222)
- Bump assertj-core from 2.6.0 to 3.11.1 [\#221](https://github.com/factcast/factcast/issues/221)
- Bump oss-parent-pom from 1.0.0 to 1.0.4 [\#220](https://github.com/factcast/factcast/issues/220)
- Bump os-maven-plugin from 1.4.1.Final to 1.6.1 [\#219](https://github.com/factcast/factcast/issues/219)
- Fix 'Trailing spaces' issue in .circleci\config.yml [\#218](https://github.com/factcast/factcast/issues/218)
- Fix 'Overload methods should not be split. Previous overloaded method located at line '104'.' issue in factcast-grpc-api\src\main\java\org\factcast\grpc\api\conv\ProtoConverter.java [\#217](https://github.com/factcast/factcast/issues/217)
- migration guide [\#216](https://github.com/factcast/factcast/issues/216)
- Add migration Guide to documentation [\#215](https://github.com/factcast/factcast/issues/215)
- store-pgsql: move auto-configuration out of module [\#214](https://github.com/factcast/factcast/issues/214)
- Issue98 JUNIT5, Spring Boot2, better example projects \(boot1.5 & boot2.1\) [\#213](https://github.com/factcast/factcast/issues/213)
- Sanitize properties [\#212](https://github.com/factcast/factcast/issues/212)
- fix verson and scope for slf4j-simple dep. [\#211](https://github.com/factcast/factcast/issues/211)
- Replace dropwizard Metrics by micrometer.io [\#210](https://github.com/factcast/factcast/issues/210)
- Switch Server-side depenencies to boot2 [\#209](https://github.com/factcast/factcast/issues/209)
- Introduce proper test categorization [\#208](https://github.com/factcast/factcast/issues/208)
- introduce impsort plugin and pre-commit hook [\#206](https://github.com/factcast/factcast/issues/206)
- Unify and enforce import order on java sources [\#205](https://github.com/factcast/factcast/issues/205)
- Issue203 [\#204](https://github.com/factcast/factcast/issues/204)
- Consider using testcontainers for local testing with postgres instead of flapdoodle [\#203](https://github.com/factcast/factcast/issues/203)
- minor changes suggested by IDEA inspection [\#202](https://github.com/factcast/factcast/issues/202)
- Consider IDEAs analysis suggestions [\#201](https://github.com/factcast/factcast/issues/201)
- Issue78 integrated spring-boot-2 branch [\#200](https://github.com/factcast/factcast/issues/200)
- Issue190 applied review comments [\#199](https://github.com/factcast/factcast/issues/199)
- \#190: added log messages [\#196](https://github.com/factcast/factcast/issues/196)
- Issue194 tried to limit memory usage again for use in circleci [\#195](https://github.com/factcast/factcast/issues/195)
- Try to improve test flakyness by using different base image [\#194](https://github.com/factcast/factcast/issues/194)
- minor changes [\#193](https://github.com/factcast/factcast/issues/193)
- Upgrade dependencies to spring-grpc [\#192](https://github.com/factcast/factcast/issues/192)
- Issue188 dependency cleanup [\#191](https://github.com/factcast/factcast/issues/191)
- Handle unavailable Nashorn engine [\#190](https://github.com/factcast/factcast/issues/190)
- Consider formatting with spotless  [\#189](https://github.com/factcast/factcast/issues/189)
- Remove direct dependencies from parent pom [\#188](https://github.com/factcast/factcast/issues/188)
- Extract dependency management to factcast-dependencies project [\#187](https://github.com/factcast/factcast/issues/187)
- \#185: added @NonNull contract to obserer.onNext [\#186](https://github.com/factcast/factcast/issues/186)
- GenericObserver.next should guarantee to not be called with null parameter [\#185](https://github.com/factcast/factcast/issues/185)
- \#183 remove use of powermock [\#184](https://github.com/factcast/factcast/issues/184)
- replace use of powermock [\#183](https://github.com/factcast/factcast/issues/183)
- \#180 Recheck PGStore notification mechanism \(follow-subscriptions sometimes seem to starve\) [\#182](https://github.com/factcast/factcast/issues/182)
- \[WIP\] Spring Boot 2 [\#181](https://github.com/factcast/factcast/issues/181)
- Recheck pg store internal notification [\#180](https://github.com/factcast/factcast/issues/180)
- \#178: codacy review [\#179](https://github.com/factcast/factcast/issues/179)
- Avoid unused local variables such as 'sub'. [\#178](https://github.com/factcast/factcast/issues/178)
- Avoid unused imports such as 'org.factcast.grpc.compression.lz4.LZ4Codec' [\#177](https://github.com/factcast/factcast/issues/177)
- \#175: added debug switch [\#176](https://github.com/factcast/factcast/issues/176)
- CLI: add --debug to log at least info-level messages to the console [\#175](https://github.com/factcast/factcast/issues/175)
- \#169: added server impl version to handshake, and log it on the client [\#174](https://github.com/factcast/factcast/issues/174)
- \#170: added index for enumeration of ns and type [\#173](https://github.com/factcast/factcast/issues/173)
- \#171: added exception message to output [\#172](https://github.com/factcast/factcast/issues/172)
- CLI: Print exception details when failing [\#171](https://github.com/factcast/factcast/issues/171)
- PSQL Store: Create index to support enumeration queries [\#170](https://github.com/factcast/factcast/issues/170)
- Add Server Implementation Version to ServerConfig [\#169](https://github.com/factcast/factcast/issues/169)
- GRPC Response Compression via Interceptor [\#168](https://github.com/factcast/factcast/issues/168)
- \#153 add Namespace and Type enumeration [\#167](https://github.com/factcast/factcast/issues/167)
- \#165: opened Fact.Builder methods [\#166](https://github.com/factcast/factcast/issues/166)
- Prettify JSON output [\#164](https://github.com/factcast/factcast/issues/164)
- CLI: add -pretty option to format JSON in output [\#163](https://github.com/factcast/factcast/issues/163)
- \#161: added serialOf to CLI [\#162](https://github.com/factcast/factcast/issues/162)
- CLI: Implement serialOf [\#161](https://github.com/factcast/factcast/issues/161)
- compression config cleanup [\#160](https://github.com/factcast/factcast/issues/160)
- Cleanup LZ4 & GZIP Configuration [\#159](https://github.com/factcast/factcast/issues/159)
- \#157: introduced FACTCAST\_SERVER env variable [\#158](https://github.com/factcast/factcast/issues/158)
- CLI: reintroduce getting defaults for -h and -p from environment [\#157](https://github.com/factcast/factcast/issues/157)
- added CLI [\#156](https://github.com/factcast/factcast/issues/156)
- Factory Method Fact.of\(JsonNode, JsonNode\) [\#155](https://github.com/factcast/factcast/issues/155)
- Open SpecBuilder [\#154](https://github.com/factcast/factcast/issues/154)
- \#149: propage exception in GrpcFactStore::publish [\#151](https://github.com/factcast/factcast/issues/151)
- \#147: provide build for fact [\#150](https://github.com/factcast/factcast/issues/150)
- WIP: ITD-55540: Added cli [\#148](https://github.com/factcast/factcast/issues/148)
- \#145: added constraints for header.ns and header.id, added migration … [\#146](https://github.com/factcast/factcast/issues/146)
- store-pgsql must not accept facts without namespace [\#145](https://github.com/factcast/factcast/issues/145)
- Issue135: add default values for tomcat properties [\#144](https://github.com/factcast/factcast/issues/144)
- \#142: some coverage improvements [\#143](https://github.com/factcast/factcast/issues/143)
- Improve coverage [\#142](https://github.com/factcast/factcast/issues/142)
- \#140: prepared codec for grpc-spring-boot-starter/issues/96 [\#141](https://github.com/factcast/factcast/issues/141)
- Add LZ4 Codecs according to upstream @GrpcCodec Feature [\#140](https://github.com/factcast/factcast/issues/140)
- \#137: enforce namespace attribute on publish [\#138](https://github.com/factcast/factcast/issues/138)
- \#135: reset RDS autoconfig to 0.0.6 [\#136](https://github.com/factcast/factcast/issues/136)
- \#133: added serialOf to GRPC Service [\#134](https://github.com/factcast/factcast/issues/134)
- Reconsider usage of spring-starter for grpc client [\#132](https://github.com/factcast/factcast/issues/132)
- \#130: cleanup, added guava,pgsql versions to BOM [\#131](https://github.com/factcast/factcast/issues/131)
- Cleanup for 0.0.14 release [\#130](https://github.com/factcast/factcast/issues/130)
- Add credentials usage on GRPC \(TLS a prereq\) [\#129](https://github.com/factcast/factcast/issues/129)
- \#123: added handshake method that transports a string map & the proto… [\#128](https://github.com/factcast/factcast/issues/128)
- Issue125 [\#127](https://github.com/factcast/factcast/issues/127)
- added autoconfig [\#126](https://github.com/factcast/factcast/issues/126)
- Upgrade grpc deps to 1.12.0 [\#125](https://github.com/factcast/factcast/issues/125)
- Implement GRPC negotiation protocol [\#123](https://github.com/factcast/factcast/issues/123)
- Issue119 [\#121](https://github.com/factcast/factcast/issues/121)
- changed favicon [\#120](https://github.com/factcast/factcast/issues/120)
- Replace favicon... [\#119](https://github.com/factcast/factcast/issues/119)
- \#117: added automatically generated changelog to site [\#118](https://github.com/factcast/factcast/issues/118)
- Automatic changelog generation from Issue & PR Data [\#117](https://github.com/factcast/factcast/issues/117)
- Issue84 [\#116](https://github.com/factcast/factcast/issues/116)
- moved rest module to extra project [\#115](https://github.com/factcast/factcast/issues/115)
- moved examples to dedicated directory [\#114](https://github.com/factcast/factcast/issues/114)
- added BOM project for version pinning [\#113](https://github.com/factcast/factcast/issues/113)
- Move server project to example-server [\#112](https://github.com/factcast/factcast/issues/112)
- Provide a convenient solution for version pinning [\#111](https://github.com/factcast/factcast/issues/111)
- Update maven-\[surefire,failsafe\]-plugin to 2.22.0 [\#110](https://github.com/factcast/factcast/issues/110)
- Upgrade junit dependency to 5.3.1 [\#109](https://github.com/factcast/factcast/issues/109)
- \#99: switched tests to embed pgsql 10 [\#108](https://github.com/factcast/factcast/issues/108)
- \#106: updated dependency for pg-embed [\#107](https://github.com/factcast/factcast/issues/107)
- Upgrade embedded postgres dependency [\#106](https://github.com/factcast/factcast/issues/106)
- Issue87 [\#105](https://github.com/factcast/factcast/issues/105)
- An instanceof check is being performed on the caught exception.  Create a separate catch clause for this exception type. [\#104](https://github.com/factcast/factcast/issues/104)
- Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes. [\#103](https://github.com/factcast/factcast/issues/103)
- Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes. [\#102](https://github.com/factcast/factcast/issues/102)
- \#86: added rpc endpoint for protocol version in grpc [\#101](https://github.com/factcast/factcast/issues/101)
- Issue96 [\#100](https://github.com/factcast/factcast/issues/100)
- Move pg to version 10 [\#99](https://github.com/factcast/factcast/issues/99)
- Migrate Tests to JUNIT5 [\#98](https://github.com/factcast/factcast/issues/98)
- Issue95 [\#97](https://github.com/factcast/factcast/issues/97)
- add repolocal githooks [\#96](https://github.com/factcast/factcast/issues/96)
- fix hugo version [\#95](https://github.com/factcast/factcast/issues/95)
- Issue79 [\#94](https://github.com/factcast/factcast/issues/94)
- use provided pgsql in circleci [\#93](https://github.com/factcast/factcast/issues/93)
- Document unique\_identifier [\#92](https://github.com/factcast/factcast/issues/92)
- unique\_identifier added. fixes \#82 [\#91](https://github.com/factcast/factcast/issues/91)
- Issue53 [\#90](https://github.com/factcast/factcast/issues/90)
- Update README.md [\#89](https://github.com/factcast/factcast/issues/89)
- Deploy Milestone 0.10 to maven central [\#88](https://github.com/factcast/factcast/issues/88)
- Document .clear in InMemFactStore [\#87](https://github.com/factcast/factcast/issues/87)
- Add Version info procedure to GRPC protocol [\#86](https://github.com/factcast/factcast/issues/86)
- Avoid embedded postgres if possible [\#85](https://github.com/factcast/factcast/issues/85)
- Extract REST code to separate repo [\#84](https://github.com/factcast/factcast/issues/84)
- In-memory FactStore clear [\#81](https://github.com/factcast/factcast/issues/81)
- \#53: add timeouts for pgsql [\#80](https://github.com/factcast/factcast/issues/80)
- Switch to JUnit5 [\#79](https://github.com/factcast/factcast/issues/79)
- Move Spring code to spring boot 2.x [\#78](https://github.com/factcast/factcast/issues/78)
- fixes \#45 [\#76](https://github.com/factcast/factcast/issues/76)
- fixes \#71 [\#75](https://github.com/factcast/factcast/issues/75)
- added global server interceptor [\#74](https://github.com/factcast/factcast/issues/74)
- add global gzip compression to all grpc communication server -\> client [\#73](https://github.com/factcast/factcast/issues/73)
- cannot run Target:  org.factcast.server.grpc.TransportLayerException: null [\#72](https://github.com/factcast/factcast/issues/72)
- Unregister call from Eventbus fails unexpectedly [\#71](https://github.com/factcast/factcast/issues/71)
- Subscriptions can become stale? [\#70](https://github.com/factcast/factcast/issues/70)
- Ignore .factorypath [\#68](https://github.com/factcast/factcast/issues/68)
- added DSGVO/GDPR declaration [\#67](https://github.com/factcast/factcast/issues/67)
- Add GDPR Declaration to website [\#66](https://github.com/factcast/factcast/issues/66)
- Upgrade circleci to 2.0 [\#65](https://github.com/factcast/factcast/issues/65)
- adapt oss-parent-pom [\#64](https://github.com/factcast/factcast/issues/64)
- Adapt to use oss-parent-pom  [\#63](https://github.com/factcast/factcast/issues/63)
- added license plugin, \#61 [\#62](https://github.com/factcast/factcast/issues/62)
- Add License plugin to project [\#61](https://github.com/factcast/factcast/issues/61)
- Remove wrapping of SQLException [\#58](https://github.com/factcast/factcast/issues/58)
- Remove wrapping of SQLException [\#57](https://github.com/factcast/factcast/issues/57)
- Upgrade dependencies [\#54](https://github.com/factcast/factcast/issues/54)
- Add some Timeouts when working with PGSQL [\#53](https://github.com/factcast/factcast/issues/53)
- Array of ids in meta data [\#52](https://github.com/factcast/factcast/issues/52)
- Issue46 rework [\#51](https://github.com/factcast/factcast/issues/51)
- testing snyk [\#50](https://github.com/factcast/factcast/issues/50)
- Add imprint [\#48](https://github.com/factcast/factcast/issues/48)
- Issue46 [\#47](https://github.com/factcast/factcast/issues/47)
- Unclosed Subscriptions via Rest [\#46](https://github.com/factcast/factcast/issues/46)
- Migrate all existing Facts by adding meta.\_ser [\#45](https://github.com/factcast/factcast/issues/45)
- updated browser REST consumer example [\#41](https://github.com/factcast/factcast/issues/41)
- issue\#38 added queued catchup strategy [\#40](https://github.com/factcast/factcast/issues/40)
- added mapping exception test [\#39](https://github.com/factcast/factcast/issues/39)
- Coverage improved. [\#37](https://github.com/factcast/factcast/issues/37)
- Rest: support for postQueryFiltering is missing? [\#36](https://github.com/factcast/factcast/issues/36)
- Closing a subscription during catchup causes IOExceptions [\#34](https://github.com/factcast/factcast/issues/34)
- data source initalization with remote aws rds call [\#33](https://github.com/factcast/factcast/issues/33)
- renamed continuous [\#32](https://github.com/factcast/factcast/issues/32)
- Issue\#30 [\#31](https://github.com/factcast/factcast/issues/31)
- Issue 23 [\#29](https://github.com/factcast/factcast/issues/29)
- typo fix [\#24](https://github.com/factcast/factcast/issues/24)
- unify schema for subscription-request between rest & grpc api [\#20](https://github.com/factcast/factcast/issues/20)
- publish schema for subscriptionrequest [\#19](https://github.com/factcast/factcast/issues/19)
- Add js based gRPC and REST consumer/producer usage exmaples [\#18](https://github.com/factcast/factcast/issues/18)
- Publishing at high rates throws [\#17](https://github.com/factcast/factcast/issues/17)
- Moving cursor without hit [\#16](https://github.com/factcast/factcast/issues/16)
- Moving cursor without hit [\#15](https://github.com/factcast/factcast/issues/15)
- renamed aggId to aggIds [\#14](https://github.com/factcast/factcast/issues/14)
- Rename aggId to aggIds [\#13](https://github.com/factcast/factcast/issues/13)
- many aggIds per Fact [\#12](https://github.com/factcast/factcast/issues/12)
- liquibase support for event store [\#11](https://github.com/factcast/factcast/issues/11)
- Supply proper metrics [\#10](https://github.com/factcast/factcast/issues/10)
- Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes. [\#9](https://github.com/factcast/factcast/issues/9)
- Avoid throwing raw exception types. [\#8](https://github.com/factcast/factcast/issues/8)
- Switch statements should have a default label [\#7](https://github.com/factcast/factcast/issues/7)
- Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes. [\#6](https://github.com/factcast/factcast/issues/6)
- Add a Codacy badge to README.md [\#5](https://github.com/factcast/factcast/issues/5)
- polish variable-/field-names [\#4](https://github.com/factcast/factcast/issues/4)
- Pimp my Readme [\#3](https://github.com/factcast/factcast/issues/3)
- remove Thread.sleep from tests [\#2](https://github.com/factcast/factcast/issues/2)
- use spring support for jersey hyper schema generator [\#1](https://github.com/factcast/factcast/issues/1)



\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
