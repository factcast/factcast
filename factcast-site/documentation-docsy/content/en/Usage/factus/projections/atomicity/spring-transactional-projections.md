+++
title = "Spring Transactional"
weight = 500
type="docs"
+++

## Broad Data-Store Support

Spring comes with [extensive support for transactions](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction)
which is employed by _Spring Transactional Projections_.

Standing on the shoulders of [Spring Transactions](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction),
Factus supports transactionality for every data-store for which Spring transaction management
is available. In more detail, for the data-store in question, an implementation of the Spring [`PlatformTransactionManager`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/PlatformTransactionManager.html)
must exist.

## Motivation

You would want to use Spring Transactional for two reasons:

- atomicity of factStreamPosition updates and your projection state updates
- increased fact processing throughput

The Performance bit is achieved by skipping unnecessary factStreamPosition updates and (more importantly) by reducing the number of transactions on your datastore by using one Transaction for `bulkSize` updates instead of single writes.
For instance, if you use Spring Transactions on a JDBC Datastore, you will have one database transaction around the update of `bulkSize` events.
The `bulkSize` is configurable per projection via the @SpringTransactional annotation.

## Configuration

In order to make use of spring transaction support, the necessary dependency has to be included in your project:

```xml
    <dependency>
        <groupId>org.factcast</groupId>
        <artifactId>factcast-factus-spring-tx</artifactId>
    </dependency>

```

## Structure

To use Spring Transactionality, a projection needs to:

- be annotated with `@SpringTransactional` to configure bulk and transaction-behavior and
- implement `SpringTxProjection` to return the responsible PlatformTransactionManager for this kind of Projection

## Applying facts

In your @Handler methods, you need to make sure you use the Spring-Managed Transaction when talking to your datastore.
This might be entirely transparent for you (for instance, when using JDBC that assigns the transaction to the current thread), or will need you to resolve the current transaction from the given `platformTransactionManager` [example](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#148-spring-transaction-manager).

Please consult the Spring docs or your driver's documentation.

{{% alert  title="Note" %}}

Factus provides convenient abstract classes for managed and subscribed projections:

- `AbstractSpringTxManagedProjection`
- `AbstractSpringTxSubscribedProjection`

{{% / alert %}}

## Coordinating writers over JDBC

Every [managed]({{< ref "managed-projection.md" >}}) and [subscribed]({{< ref "subscribed-projection.md" >}}) projection
has to hand out a `WriterToken`, so that only one instance of your application writes to it at a time.
The turnkey implementations for that used to be Redis- and MongoDB-based, which meant operating one
of those just for the write token. On a relational datastore that is not necessary: Factus can keep
both the write token and the fact-stream-position in the very same database your projection already
writes to.

To get both, extend one of

- `AbstractSpringJdbcManagedProjection`
- `AbstractSpringJdbcSubscribedProjection`

instead of the `AbstractSpringTx*` classes above and pass a `JdbcTemplate` alongside the
`PlatformTransactionManager`:

```java
@ProjectionMetaData(revision = 1)
@SpringTransactional
public class UserNames extends AbstractSpringJdbcManagedProjection {

    private final JdbcTemplate jdbcTemplate;

    public UserNames(
            @NonNull PlatformTransactionManager platformTransactionManager,
            @NonNull JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager, jdbcTemplate);
        this.jdbcTemplate = jdbcTemplate;
    }
    ...
```

That is all: `acquireWriteToken`, `factStreamPosition()` and `factStreamPosition(...)` are inherited, so
only your `@Handler` methods and your query methods are left to write.

If your projections already sit in a class hierarchy of their own, `JdbcWriterTokenManager` is public
API and implements `acquireWriteToken` on its own:

```java
private final JdbcWriterTokenManager writerTokenManager;

public UserNames(
        @NonNull PlatformTransactionManager platformTransactionManager,
        @NonNull JdbcTemplate jdbcTemplate) {
    super(platformTransactionManager);
    this.writerTokenManager =
            JdbcWriterTokenManager.create(jdbcTemplate, getScopedName().asString());
}

@Override
public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return writerTokenManager.acquireWriteToken(maxWait);
}
```

### Required schema

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

### Lease semantics

The write token is a lease, not a permanent lock. It is held for 60 seconds and renewed in the
background every 20 seconds, so an instance that dies without releasing its token blocks its
replacement for at most one lease. `WriterToken.isValid()`, which Factus checks for every batch of
facts, reports `false` as soon as the last successful renewal is older than the lease. That is what
stops an instance that lost its lease from writing on.

Every instance identifies itself by hostname _and_ a random id, so two instances on the same host
(or in the same JVM) cannot renew or release each other's lease.

{{% alert title="Note" color="warning" %}}

Acquiring, renewing and releasing the lease each run in their own transaction, which means a
**second** connection while your projection's transaction holds one. A connection pool of size 1
deadlocks.

{{% / alert %}}

You can find blueprints of getting started in the [example section](/usage/factus/projections/example).
