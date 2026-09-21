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

[Write Tokens over JDBC]({{< ref "jdbc-write-tokens.md" >}}) keeps both the write token and the
fact-stream-position in the very same relational database your projection already writes to, so
that no Redis or MongoDB is needed just for the write token. That page carries the required schema,
the supported databases and the lease semantics; all of it applies here unchanged.

What the Spring variant adds is **atomicity**: the position is written on the connection of the
ongoing transaction, so it commits together with your projection's own updates. Extend one of

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

The lock itself deliberately does **not** join your transaction: it is taken on a connection of its
own, so the lease is committed as it is taken and survives a rollback of the projection's
transaction. That means a **second** connection while your projection's transaction holds one — a
connection pool of size 1 deadlocks.

`AbstractSpringJdbcSubscribedProjection` offers `hasLock()` to your subclass, to gate work that is
triggered from outside the fact stream:

```java
@Scheduled(fixedRate = 60_000)
public void pruneStaleRows() {
    if (hasLock()) {
        jdbcTemplate.update("DELETE FROM users WHERE ...");
    }
}
```

You can find blueprints of getting started in the [example section](/usage/factus/projections/example).
