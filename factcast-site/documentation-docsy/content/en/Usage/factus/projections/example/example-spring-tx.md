+++
title = "UserNames (Spring/JDBC)"
type = "docs"
weight = 52
+++

Here is an example for a managed projection externalizing its state to a relational database (PostgreSQL here) using Spring transactional management.

The example projects a list of used UserNames in the System.

## Preparation

We need to store three things in our JDBC Datastore:

- the actual list of UserNames,
- the fact-stream-position of your projection, and
- the write token that keeps two instances of our application from writing to the projection at the same time.

Only the first one is specific to this projection, so we create it ourselves:

```sql
CREATE TABLE users (
    name TEXT,
    id UUID,
    PRIMARY KEY (id));
```

The other two are handled by Factus, provided the tables it expects exist (probably created using liquibase/flyway or similar tooling of your choice):

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

Both are shared by all of our JDBC projections, keyed by a unique projection name. Note that Factcast will not
create or migrate these tables for you: its own Liquibase changesets run against the _store_ database, never
against your projection database. See [Spring Transactional]({{< ref "spring-transactional-projections.md" >}})
for the details, including the table a _subscribed_ projection uses instead of `managed_projection`.

## Constructing

Since we decided to use a managed projection, we extended the `AbstractSpringJdbcManagedProjection` class.
To configure transaction management, our managed projection exposes the injected transaction manager to the rest of Factus by calling the parent constructor.
The `JdbcTemplate` we pass along is what Factus uses to maintain the fact-stream-position and the write token.

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

As we're making use of Spring here, we inject a `PlatformTransactionManager` and a `JdbcTemplate` here in order to communicate with the database in a transactional way.

Two remarks:

1. As soon as your project uses the `spring-boot-starter-jdbc` dependency,
   Spring Boot will [automatically provide](https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jdbc/DataSourceTransactionManagerAutoConfiguration.java)
   you with a [JDBC-aware PlatformTransactionManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/support/JdbcTransactionManager.html).
2. To ensure that the database communication participates in the managed transaction,
   the database access mechanism must be also provided by Spring. Thus, we suggest using the `JdbcTemplate`.

## Configuration

The `@SpringTransactional` annotation provides various configuration options:

| Parameter Name     | Description        | Default Value |
| ------------------ | ------------------ | ------------- |
| `bulkSize`         | bulk size          | 50            |
| `timeoutInSeconds` | timeout in seconds | 30            |

## Updating the projection

### Applying Facts

When processing the _UserCreated_ event, we add a new row to the `users` tables, filled with event data:

```java
@Handler
void apply(UserCreated e) {
    jdbcTemplate.update(
            "INSERT INTO users (name, id) VALUES (?,?);",
            e.userName(),
            e.aggregateId());
}
```

When handling the _UserDeleted_ event we do the opposite and remove the appropriate row:

```java
@Handler
void apply(UserDeleted e) {
    jdbcTemplate.update("DELETE FROM users where id = ?", e.aggregateId());
}
```

That is all the event-processing code there is: writing the fact-stream-position inside the very same
transaction, and holding the write token while doing so, is what the abstract class takes care of.
What is missing is a way to make the projection's data accessible for users.

## Querying the projection

Users of our projections (meaning "other code") contact the projection via it's public API.
Currently, there is no public method offering "user names". So let's change that:

```java
public List<String> getUserNames() {
    return jdbcTemplate.query("SELECT name FROM users", (rs, rowNum) -> rs.getString(1));
}
```

### Using The Projection

Calling code that wants to talk to the projection, now just needs to call the `getUserNames` method:

```java
// create a local instance or get a Spring Bean from the ApplicationContext, depending on your code organization
UserNames userNameProjection = new UserNames(platformTransactionManager, jdbcTemplate);

// depending on many factors you *may* want to update the projection before querying it
factus.update(userNameProjection);

List<String> userNames = userNameProjection.getUserNames();
```

First, we create an instance of the projection and provide it with all required dependencies. As an alternative, you may want to let Spring manage the lifecycle of the projection
and let the dependency injection mechanism provide you an instance.

Next, we call `update(...)` on the projection to fetch the latest events from the Fact stream. Note that when you use a pre-existing (maybe Spring managed singleton) instance of the projection, this step is optional and depends on your use-case. As last step, we ask
the projection to provide us with user names by calling `getUserNames()`.

## Bringing your own tables

If you have to keep an existing schema, extend `AbstractSpringTxManagedProjection` (or
`AbstractSpringTxSubscribedProjection`) instead and implement the three methods yourself:

| Method Signature                                                                 | Description                                                                                             |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `public FactStreamPosition factStreamPosition()`                                 | read the last position in the Fact stream from the database                                             |
| `public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition)` | write the current position of the Fact stream to the database                                           |
| `public WriterToken acquireWriteToken(@NonNull Duration maxWait)`                | coordinates write access to the projection, see [here]({{< ref "managed-projection.md" >}}) for details |

Provided a table `fact_stream_positions` exists, here is an example of how to write the Fact position:

```java
@Override
public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
    jdbcTemplate.update(
            "INSERT INTO fact_stream_positions (projection_name, fact_stream_position) " +
            "VALUES (?, ?) " +
            "ON CONFLICT (projection_name) DO UPDATE SET fact_stream_position = ?",
            getScopedName().asString(),
            factStreamPosition.factId(),
            factStreamPosition.factId());
}
```

For convenience, an UPSERT statement (Postgres syntax) is used, which INSERTs the UUID the first time
and subsequently only UPDATEs the value.

To avoid hard-coding a unique name for the projection, the provided method `getScopedName()` is employed.
The default implementation makes sure the name is unique and includes the revision of the projection.

To read the last Fact stream position, we simply select the previously written value, returning `null`
in case no previous Fact position exists:

```java
@Override
public FactStreamPosition factStreamPosition() {
    try {
        return FactStreamPosition.withoutSerial(
                jdbcTemplate.queryForObject(
                        "SELECT fact_stream_position FROM fact_stream_positions WHERE projection_name = ?",
                        UUID.class,
                        getScopedName().asString()));
    } catch (IncorrectResultSizeDataAccessException e) {
        // no position yet, just return null
        return null;
    }
}
```

The write token, on the other hand, is worth reusing rather than reimplementing: `JdbcWriterTokenManager`
is public API and only needs the lock table from the [Preparation](#preparation) section.

```java
private final JdbcWriterTokenManager writerTokenManager;

public UserNames(
        @NonNull PlatformTransactionManager platformTransactionManager,
        @NonNull JdbcTemplate jdbcTemplate) {
    super(platformTransactionManager);
    this.jdbcTemplate = jdbcTemplate;
    this.writerTokenManager =
            JdbcWriterTokenManager.create(jdbcTemplate, getScopedName().asString());
}

@Override
public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return writerTokenManager.acquireWriteToken(maxWait);
}
```

## Full Example

To study the full example see

- [the UserNames projection using `@SpringTransactional`](https://github.com/factcast/factcast/blob/main/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/proj/SpringJdbcTransactionalProjectionExample.java),
- [example code using this projection](https://github.com/factcast/factcast/blob/main/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/client/SpringJdbcTransactionalProjectionExampleITest.java),
- [the write token and fact-stream-position integration tests](https://github.com/factcast/factcast/blob/main/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/client/SpringJdbcProjectionLockITest.java) and
- [the Factus integration tests](https://github.com/factcast/factcast/blob/main/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/client/SpringTransactionalITest.java) including managed- and subscribed projections.
