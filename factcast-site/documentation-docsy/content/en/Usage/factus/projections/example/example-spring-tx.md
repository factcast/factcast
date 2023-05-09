+++ title = "UserNames (Spring/JDBC)"
type = "docs"
weight = 52

+++

Here is an example for a managed projection externalizing its state to a relational database (PostgreSQL here) using Spring transactional management.

The example projects a list of used UserNames in the System.

## Preparation

We need to store two things in our JDBC Datastore:

- the actual list of UserNames, and
- the fact-stream-position of your projection.

Therefore we create the necessary tables (probably using liquibase/flyway or similar tooling of your choice):

```sql
CREATE TABLE users (
    name TEXT,
    id UUID,
    PRIMARY KEY (id));
```

```sql
CREATE TABLE fact_stream_positions (
    projection_name TEXT,
    fact_stream_position UUID,
    PRIMARY KEY (projection_name));
```

Given a unique projection name, we can use _fact_stream_positions_ as a common table for all our JDBC managed projections.

{{% alert  title="TODO" color="warning" %}}

provide example for acquireWriteToken based on JDBC

{{% / alert %}}

## Constructing

Since we decided to use a managed projection, we extended the `AbstractSpringTxManagedProjection` class.
To configure transaction management, our managed projection exposes the injected transaction manager to the rest of Factus by calling the parent constructor.

```java
@ProjectionMetaData(serial = 1)
@SpringTransactional
public class UserNames extends AbstractSpringTxManagedProjection {

    private final JdbcTemplate jdbcTemplate;

    public UserNames(
            @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager);
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

|   Parameter Name   |    Description     | Default Value |
|--------------------|--------------------|---------------|
| `bulkSize`         | bulk size          | 50            |
| `timeoutInSeconds` | timeout in seconds | 30            |

## Updating the projection

The two possible abstract base classes, `AbstractSpringTxManagedProjection` or `AbstractSpringTxSubscribedProjection`,
both require the following methods to be implemented:

|                          Method Signature                          |                                               Description                                               |
|--------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `public UUID factStreamPosition()   `                              | read the last position in the Fact stream from the database                                             |
| `public void factStreamPosition(@NonNull UUID factStreamPosition)` | write the current position of the Fact stream to the database                                           |
| `public WriterToken acquireWriteToken(@NonNull Duration maxWait)`  | coordinates write access to the projection, see [here]({{< ref "managed-projection.md" >}}) for details |

The first two methods tell Factus how to read and write the Fact stream's position from the database.

### Writing the fact position

Provided the table `fact_stream_positions` exists, here is an example of how to write the Fact position:

```java
@Override
public void factStreamPosition(@NonNull UUID factStreamPosition) {
    jdbcTemplate.update(
            "INSERT INTO fact_stream_positions (projection_name, fact_stream_position) " +
            "VALUES (?, ?) " +
            "ON CONFLICT (projection_name) DO UPDATE SET fact_stream_position = ?",
            getScopedName().asString(),
            factStreamPosition,
            factStreamPosition);
}
```

For convenience, an UPSERT statement (Postgres syntax) is used, which INSERTs the UUID the first time
and subsequently only UPDATEs the value.

To avoid hard-coding a unique name for the projection, the provided method `getScopedName()` is employed.
The default implementation makes sure the name is unique and includes the serial of the projection.

### Reading the fact position

To read the last Fact stream position, we simply select the previously written value:

```java
@Override
public UUID factStreamPosition() {
    try {
        return jdbcTemplate.queryForObject(
                "SELECT fact_stream_position FROM fact_stream_positions WHERE projection_name = ?",
                UUID.class,
                getScopedName().asString());
    } catch (IncorrectResultSizeDataAccessException e) {
        // no position yet, just return null
        return null;
    }
}
```

In case no previous Fact position exists, `null` is returned.

### Applying Facts

When processing the _UserCreated_ event, we add a new row to the `users` tables, filled with event data:

```java
@Handler
void apply(UserCreated e) {
    jdbcTemplate.update(
            "INSERT INTO users (name, id) VALUES (?,?);",
            e.getUserName(),
            e.getAggregateId());
}
```

When handling the _UserDeleted_ event we do the opposite and remove the appropriate row:

```java
@Handler
void apply(UserDeleted e) {
    jdbcTemplate.update("DELETE FROM users where id = ?", e.getAggregateId());
}
```

We have finished the implementation of the event-processing part of our projection. What is missing is a way to
make the projection's data accessible for users.

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

## Full Example

To study the full example see

- [the UserNames projection using `@SpringTransactional`](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/proj/SpringJdbcTransactionalProjectionExample.java),
- [example code using this projection](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/SpringJdbcTransactionalProjectionExampleITest.java) and
- [the Factus integration tests](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/SpringTransactionalITest.java) including managed- and subscribed projections.

