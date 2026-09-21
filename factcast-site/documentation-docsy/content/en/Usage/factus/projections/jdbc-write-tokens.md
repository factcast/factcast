+++
title = "Write Tokens over JDBC"
weight = 55
type = "docs"
+++

Every [managed]({{< ref "managed-projection.md" >}}) and [subscribed]({{< ref "subscribed-projection.md" >}}) projection
has to hand out a `WriterToken`, so that only one instance of your application writes to it at a
time. The turnkey implementations for that used to be Redis- and MongoDB-based, which meant
operating one of those just for the write token. On a relational datastore that is not necessary:
Factus can keep both the write token and the fact-stream-position in the very same database your
projection already writes to.

## Configuration

```xml
    <dependency>
        <groupId>org.factcast</groupId>
        <artifactId>factcast-factus-jdbc</artifactId>
    </dependency>
```

The module talks plain JDBC and brings no Spring dependency of its own.

## Structure

Extend one of

- `AbstractJdbcManagedProjection`
- `AbstractJdbcSubscribedProjection`

and pass a `DataSource`:

```java
@ProjectionMetaData(revision = 1)
public class UserNames extends AbstractJdbcManagedProjection {

    public UserNames(@NonNull DataSource dataSource) {
        super(dataSource);
    }
    ...
```

That is all: `acquireWriteToken`, `factStreamPosition()` and `factStreamPosition(...)` are inherited,
so only your `@Handler` methods and your query methods are left to write. The `DataSource` you
passed is available to them as `dataSource()`.

{{% alert title="Note" color="warning" %}}

These classes give you the write token and the fact stream position, but **no atomicity** between
the position update and your projection's own updates: the position is written on its own
connection. If a crash between the two would leave your projection inconsistent, use
[Spring Transactional]({{< ref "spring-transactional-projections.md" >}}) instead, whose
`AbstractSpringJdbc*` classes write the position on the transaction's connection.

{{% / alert %}}

If your projections already sit in a class hierarchy of their own, `JdbcWriterTokenManager` is
public API and implements `acquireWriteToken` on its own:

```java
private final JdbcWriterTokenManager writerTokenManager;

public UserNames(@NonNull DataSource dataSource) {
    this.writerTokenManager =
            JdbcWriterTokenManager.create(dataSource, getScopedName().asString());
}

@Override
public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return writerTokenManager.acquireWriteToken(maxWait);
}
```

## Required schema

The tables are **yours**, not Factus'. Factcast ships Liquibase changesets for the _store_ database
only, and a projection database is a different database that Factcast never migrates. Create the
tables with liquibase/flyway or whatever tooling you already use:

```sql
CREATE TABLE factcast_projection_locks (
    name       varchar(255) NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
```

```sql
CREATE TABLE managed_projection (
    name   varchar(255),
    state  UUID,
    serial bigint DEFAULT -1,

    PRIMARY KEY (name)
);
```

```sql
CREATE TABLE subscribed_projection (
    name   varchar(255),
    state  UUID,
    serial bigint DEFAULT -1,

    PRIMARY KEY (name)
);
```

All three are shared by every projection of your application: the row key is the projection's
`getScopedName()`, so bumping a projection's revision starts a new row. Managed projections use
`managed_projection`, subscribed ones `subscribed_projection`; create only the one you need.
Should you want different table names, both abstract classes offer a constructor taking a lock
table name and a position table name.

{{% alert title="Note" color="warning" %}}

`name` is `varchar(255)` on purpose. Lock names are built as `<projection class>_<revision>_lock` and
easily exceed the 64 characters that [ShedLock](https://github.com/lukas-krecan/ShedLock), the library
underneath, documents for its own table. A column that is too narrow does not fail as "somebody else
holds the lock": it fails with a constraint violation, surfacing as a `LockException` when the
projection tries to acquire its token.

{{% / alert %}}

## Supported databases

The lease is timed by the **database's** clock, not by the clock of the instance that holds it, so
instances whose clocks drift apart cannot outlive each other's lease. That is ShedLock's
`usingDbTime()`, and Factus switches it on unconditionally. ShedLock 7.10.0 ships those statements
for:

- PostgreSQL (and CockroachDB, which reports itself as PostgreSQL)
- MySQL and MariaDB
- Oracle
- Microsoft SQL Server
- DB2
- H2 and HSQLDB

Every other database fails on the first attempt to acquire a token, with a `LockException` that
says so. ShedLock reads the product from the JDBC metadata of the first connection it gets and
treats a product it could not read as unsupported, so the same error can also mean the database was
unreachable at that very moment.

## Lease semantics

The write token is a lease, not a permanent lock. It is held for 60 seconds and renewed in the
background every 20 seconds, so an instance that dies without releasing its token blocks its
replacement for at most one lease. `WriterToken.isValid()`, which Factus checks for every batch of
facts, reports `false` as soon as the last successful renewal is older than the lease. That is what
stops an instance that lost its lease from writing on.

Every instance identifies itself by hostname _and_ a random id, so two instances on the same host
(or in the same JVM) cannot renew or release each other's lease.

A renewal that fails because the database could not be reached is retried on the next turn instead
of ending the token right away, so a connection blip does not cost you the projection. Should the
renewals keep failing, the token turns invalid anyway once the lease has run out.

`JdbcWriterTokenManager` lets you pick another lease length, down to a second. Anything shorter is
rejected: renewals run every third of the lease, so a lease of milliseconds would put the keepalive
into a hot loop against your database.

## Waiting for the lock

`acquireWriteToken(maxWait)` retries with an exponential backoff, starting at 500ms and capped at
30 seconds, until `maxWait` is up, and then returns `null`. A database it cannot reach counts as a
failed attempt rather than an error, so a restarting database or a briefly exhausted connection
pool does not abort the caller.

How long that wait is depends on who asks:

- `factus.subscribeAndBlock(projection)` waits 5 minutes per attempt by default and keeps trying
  until it gets a token, so a subscription starts as soon as the other instance is gone.
- `factus.update(projection)` on a managed projection goes through `ManagedProjection.withLock`,
  which asks for the token with `FactusConstants.FOREVER` (365 days). While another instance holds
  the lock, `update` therefore **blocks** instead of failing. That is not limited to a holder that
  died without releasing its token: an instance that is alive and keeps the lock blocks the others
  for as long as it runs. The `update(projection, maxWaitTime)` overload does not change that, as
  its timeout never reaches the token. If you need `update` to give up, acquire the token yourself,
  for instance through `JdbcWriterTokenManager`.

## Knowing whether you hold the lock

`AbstractJdbcSubscribedProjection` remembers the token it handed to Factus and offers `hasLock()`
to your subclass. Use it to gate work on the projection that is triggered from outside the fact
stream, like a cleanup schedule or an HTTP endpoint, so that only the instance which is actually
writing does it:

```java
@Scheduled(fixedRate = 60_000)
public void pruneStaleRows() {
    if (hasLock()) {
        // DELETE FROM users WHERE ...
    }
}
```

It reports `false` before the first token was acquired, after the subscription closed, and as soon
as the lease behind the current token has run out. Managed projections have no equivalent: they hold
their token only for the duration of a single `factus.update(...)`.

{{% alert title="Note" color="warning" %}}

Acquiring, renewing and releasing the lease each run on their own connection, taken from the
`DataSource` while your projection may be holding one of its own. A connection pool of size 1
deadlocks.

{{% / alert %}}
