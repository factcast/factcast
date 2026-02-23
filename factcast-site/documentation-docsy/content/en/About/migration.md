+++
title = "Migration Guide"
type = "docs"
weight = 100015
+++

## Upgrading to 0.10.0

Version 0.10.0 switched from `net.devh:grpc-client-spring-boot-starter` to `org.springframework.grpc:spring-grpc-client-spring-boot-starter`.

The following properties should be renamed accordingly:

| Old Property                                     | New Property                                                     |
| ------------------------------------------------ | ---------------------------------------------------------------- |
| `grpc.client.factstore.address`                  | `spring.grpc.client.channels.factstore.address`                  |
| `grpc.client.factstore.negotiation-type`         | `spring.grpc.client.channels.factstore.negotiation-type`         |
| `grpc.client.factstore.enable-keep-alive`        | `spring.grpc.client.channels.factstore.enable-keep-alive`        |
| `grpc.client.factstore.keep-alive-time`          | `spring.grpc.client.channels.factstore.keep-alive-time`          |
| `grpc.client.factstore.keep-alive-without-calls` | `spring.grpc.client.channels.factstore.keep-alive-without-calls` |
| `grpc.server.permit-keep-alive-without-calls`    | `spring.grpc.server.keep-alive.permit-without-calls`             |
| `grpc.server.permit-keep-alive-time`             | `spring.grpc.server.keep-alive.permit-time`                      |

## Upgrading to 0.8.0

Version 0.8.0 introduces changes to snapshot serialization, impacting the management of Redisson snapshots.

## Postgres Version

The FactCast server now requires at least Postgres 15. If you are using Postgres 14 or below, you have to upgrade your
database before migrating to FactCast 0.8.0.

## Changes to Projections

### Transactional

Please note that, even though the max bulk size of a transaction in Factus can be configured per projection (by
implementing maxBatchSizePerTransaction or by annotation), the maximum size of a transaction bulk is ultimately limited
by the value of
`factcast.grpc.client.maxInboundMessageSize`, which defaults to 3.5MB and can be configured up to 32MB max.

### @RedisTransactional

For transactional Redis projections Factus now provides abstract classes. Projections that are annotated with
`@RedisTransactional` now must extend `AbstractRedisTxManagedProjection` or `AbstractRedisTxSubscribedProjection`
respectively. The annotation itself is optional, if no default settings are overwritten.

Please note that the default bulk size has changed from 50 to 1000.

### @RedisBatched

The `@RedisBatched` annotation has been removed. Refactor your affected projections to extend the
respective [RedisTx]({{< ref "redis-transactional-projections.md" >}})
class.

- Further configuration of the batching behaviour is possible via the optional `@RedisTransactional` annotation.
- Handlers with a `RBatch` parameter now receive an `RTransaction` object instead. If you've previously used
  `batch.addAsync()`
  you can now apply the changes directly on the transaction using e.g. `transactionalSet.add()`.

Be aware that Redisson transactions do not support all `RObjects` (e.g. Multimaps or AtomicLong). In these cases you
have to decide if you can continue without transactionality, or adapt your projection's implementation.

### @SpringTransactional

For transactional Spring projections Factus now provides abstract classes. Projections that are annotated with
`@SpringTransactional` now must extend `AbstractSpringTxManagedProjection` or `AbstractSpringTxSubscribedProjection`
respectively. The annotation itself is optional, if no default settings are overwritten.

Please note that the default bulk size has changed from 50 to 1000.

## Removal of deprecated field in ProjectionMetaData

The deprecated field `serial` field was finally removed from `ProjectionMetaData`. Please use `revision` instead like in
the following example:

```java

@ProjectionMetaData(revision = 1)
public class MyProjection extends AbstractManagedProjection { ...
}
```

## Removal of deprecated dependencies for Compression

`org.xerial.snappy` and `net.jpountz.lz4` have been removed with version 0.8.0.
Please add the new `factcast-grpc-snappy` and `factcast-grpc-lz4` (preferred) dependencies in order to enable
compression via snappy or lz4 respectively.
Further documentation can be found [here]({{< ref "compression.md" >}}).

### Redisson Snapshots

If you use Factus for aggregates or snapshot projections you now have to configure where to store the SnapshotCache.
Further documentation can be found [here]({{< ref "snapshot-caching.md" >}}). Some properties were renamed and will be
automatically migrated by Spring for a while.

Ssnapshots are now stored under different keys in Redis using
a simpler
[ByteArrayCodec](https://github.com/redisson/redisson/blob/main/redisson/src/main/java/org/redisson/client/codec/ByteArrayCodec.java)
codec.

The migration to new snapshots happens automatically. Old snapshots will remain in Redis for the duration specified by
the `factcast.redis.deleteSnapshotStaleForDays` property (default: 90 days), even though they are no longer read. This
has two implications:

1. You can roll back to the previous version of FactCast without losing any snapshots for a certain number of days.
2. During this period, Redis will store both old and new snapshots, consuming more memory. You can manually delete the
   old snapshots if you are certain they are no longer needed.

## Upgrading to 0.4.1

_This only applies if you used 0.4.0 on you data before._

If you rely on the header meta-attributes '\_ser' or '\_ts', there was a bug in 0.4.0 that has the consequence, of some
events missing the mentioned attributes.

In order to add them where they are missing, you would want to execute the following SQL on your factstore database:

```sql
update fact
set header =
        jsonb_set(header, '{meta}',
                  COALESCE(header -> 'meta', '{}')
                      || concat('{"_ser":', ser,
                                ', "_ts":', EXTRACT(EPOCH FROM now()::timestamptz(3)) * 1000, '}')::jsonb
            , true)
WHERE (header ->> 'meta')::jsonb ->> '_ser' is null
```

Obviously, the timestamp will not be correct here, but at least there is one. If you have a better indication of
the publishing time in the payload of your events, you may want to use that one instead.

## Upgrading to 0.4.0

_Please make sure you followed the migration guide if your current version is <0.3.10._

#### Building

- Java11 runtime is required for building and running the server
- lombok val is disallowed by configuration

  Please use Java11's final var instead.

#### Server

- Java11 runtime is required for building and running the server

- Default Postgres version shifted from 9.6 to 11.5

  While there is no indication that FactCast won't run on 9.x or 10.x we test against 11 now, (as 14 already is around
  the corner)

- Property namespace for the store has been changed from `org.factcast.store.pg` to `org.factcast.store`.

  While 0.4.0 still supports
  the older namespace, it is deprecated and should be migrated asap. Note that new properties are only added to the
  new namespace, so please adjust your projects accordingly.

- Note that the default catchup strategy was changed from PAGED or TMPPAGED to FETCHING. Make sure your postgres does
  not
  timeout connections.

#### Client

- The Subscription default for 'maxBatchDelayInMs' changed from **0**msec to **10**msec

  We feel like this is a good compromise between reducing the server load and minimizing latency. If you absolutely need
  minimum latency, you can still set it to 0 on a per-subscription basis.

- The default for `factcast.grpc.client.catchup-batchsize` is set to **50**.

  If, for some weird reason, you don't want the transfer to be batched, you can set this down to 1 again.

- The default for `factcast.grpc.client.id` is `${spring.application.name}`

  The id is used for logging purposes on the server.

## Upgrading to 0.3.10

0.3.10 changes the namespaces of the metrics. Also some metric names have
been changed for consistency reasons. If you created dashboards for example based on those
names, please be prepared to update them accordingly.
You can find the current metric names [here](/setup/metrics).

## Upgrading to 0.3.0

There is a new module for use in projects that provide 'EventObjects' but do not want to depend on factcast-core, which
is called "factcast-factus-event".
@Specification and interface EventObject have been moved there - please update your imports accordingly if necessary.

Another new module is factcast-test which is supposed to help with integration tests by adding a capability to
dynamically alter namespaces in integration tests.
See [testing section](/usage/factus/testing)

`factcast.lockGlobally()` **has been removed**. By adding the new locking capabilities, that define the scope of the
lock not
just by aggregate-ids, but by FactSpecs, 'lockGlobally()' needed to be deleted, as there is no proper way to provide a
compatible
migration path.
As most of the internals of FactCast rely on namespaces (for instance for authorization), you really need to enumerate
all possible namespaces that are in scope for the lock. Please use `factcast.lock(List<FactSpec>)` instead. Sorry for
the inconvenience.

## Upgrading to 0.2.1

#### Reconnection behavior

Reconnecting due to connection/unknown errors has changed from an infinite loop to just try five times (in a window of
three seconds) and escalate if they failed.
Details can be found [here](https://github.com/factcast/factcast/issues/889).

## Upgrading to 0.2.0 (quite a lot)

#### header field 'aggId' replaced by 'aggIds'

This change was actually two years ago, but it was not documented well, and if you used certain factory methods of
DefaultFact, a field 'aggId' was created/read.
So, to make it perfectly clear, a Fact can have an 'aggIds' header field, which is an array of uuids.

A FactSpec, however has an optional 'aggId' (without the 's'), as it is not an array, but a single one.
We encourage you to update any header in your postgres, that contains 'aggId', rather than 'aggIds' in the course of
migration to 0.2.0

#### basic-auth setup has changed

If you used a 'factcast-security.json' before, you will be please to learn that FactCast was extended to support
role/namespace based authorisation. Also the filename changed to 'factcast-access.json'.

see [basicauth usage](/setup/grpc-config-basicauth)

#### basic-auth setup is enforced

By default, when executing without security enabled, you need to supply a property
'factcast.security.enabled=false' via commandline or propertyfile to get away with just a warning. If you don't,
FactCast will exit with errorcode 1.

#### fetching facts by ID has been extended

Next to fetchById(UUID) (asking for the event exactly the way it was published), there is a new fetchByIdAndVersion(
UUID,int), that makes FactCast try to transform the event up/down to the requested version. Of course, the usage of a
schema-registry is a precondition, as it provides the code to do that transformation.

## Upgrading to 0.1.0

#### unique_identifier

If you used the unique*identifier feature before, it was removed. It was only a rouge hack that was used to coordinate
two instance in case of publishing. By now, coordination can be done via optimistic locking, so that the need for
\_unique_identifier* is no longer there.

#### optimistic locking

There is a [section on optimistic locking](/usage/lowlevel/java/optimistic_locking/) as a new api feature.

#### Postgres module uuid-ossp

The Postgres module _uuid-ossp_ is necessary for the new optimistic locking api feature. In order to install this
extension, the user performing the Liquibase operations requires Postgres superuser permissions.

#### GRPC Protocol Version

The GRPC Protocol Version shifted from 1.0.0 to 1.1.0. That means, in order to talk to a FactCast server with version
0.1.0, you can use and client from 0.0.30 on, but in order to use a 0.1.0 client, you'd need to talk to a FactCast
server with at least the same protocol version than your client.
So the idea is: first update your servers, then update the clients.

#### GRPC Addresses, Hosts, Ports

We updated to yidongnan/grpc-spring-boot-starter. In order to direct your client to a particular target address of a
FactCast server, you might have specified:

```
grpc.client.factstore.port=9443
grpc.client.factstore.host=localhost
```

this was replaced by

```
grpc.client.factstore.address=static://localhost:9443
```

or

```
grpc.client.factstore.address=dns:///some.host:9443
```

see https://github.com/yidongnan/grpc-spring-boot-starter for details

## Upgrading to 0.0.30

#### Spring Boot 2

If you use Spring boot, please note, that all projects now depend on Spring Boot 2 artifacts.
Support for Spring Boot 1.x was removed.

#### Plaintext vs TLS

There was a dependency upgrade of [grpc-spring-boot-starter](https://github.com/yidongnan/grpc-spring-boot-starter) in
order to support TLS. Note that the default client configuration is now switched to TLS. That means, if you want to
continue communicating in an unencrypted fashion, you need to set an application property of **'
grpc.client.factstore.negotiation-type=PLAINTEXT'**.

#### Testcontainers / Building and Testing

In order to run integration tests, that need a Postgres to run, FactCast now
uses [Testcontainers](https://www.testcontainers.org/usage/database_containers.html) in order to download and run an
ephemeral Postgres.
For this to work, the machine that runs test must have docker installed and the current user needs to be able to run and
stop docker containers.

You can still override this behavior by supplying an Environment-Variable **'pg_url'** to use a particular postgres
instead. This might be important for build agents that themselves run within docker and do not provide Docker-in-Docker.

## Upgrading to 0.0.14

- Incompatible change in GRPC API

The GRPC API has changed to enable non-breaking changes later. (Version endpoint added)
The result is, that you have to use > 0.0.14 on Client and Server consistently.

## Noteworthy 0.0.12

- Note that the jersey impl of the REST interface has its
  own <a href="https://github.com/Mercateo/factcast-rest-jersey">place on github now.</a> and got new coordinates: \*
  \*org.factcast:factcast-server-rest-jersey:0.0.12.\*\* If you use the REST Server, you'll need to change your
  dependencies accordingly

- There is a BOM within FactCast at org.factcast:factcast-bom:0.0.12 you can use to conveniently pin versions - remember
  that factcast-server-rest-jersey might not be available for every milestone and is not part of the BOM
