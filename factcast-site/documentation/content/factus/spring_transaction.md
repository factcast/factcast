+++
draft = false
title = "Spring Transaction"
description = ""


creatordisplayname = "Maik Töpfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"


parent = "factus-transactional-event-application"
identifier = "spring-transaction"
weight = 1021

+++

- TODO mention Postgres here - probably not. It's DB independent 
- TODO wording - "Fact position" instead of state
- TODO wording: "application transaction" > "transactional projections"
- TODO are there Spring transactional subscribed projs possible? should we document them as well ?
- TODO: I don't see a bean providing the PlatformTransactionManager 
- TODO: understand shared transaction between PlatformTransactionManager and JDBC template
--------------------

Transactional projections for relational databases are available for 
- [managed projections]({{< ref "managed-projection.md" >}})  and for 
- [subscribed projections]({{< ref "subscribed-projection.md" >}}).

Generally such a projection has three ingredients:
- it is annotated with `@SpringTransactional`
- it extends either 
    - the class `AbstractSpringTxManagedProjection` or 
    - the class `AbstractSpringTxSubscribedProjection`
- it provides the serial number of the projection via the `@ProjectionMetaData` annotation

Here is an example:

```java
@ProjectionMetaData(serial = 1)
@SpringTransactional
public class ExampleSpringTxManagedProjection extends AbstractSpringTxManagedProjection {

    private final JdbcTemplate jdbcTemplate;

    public ExampleSpringTxManagedProjection(
            @NonNull PlatformTransactionManager platformTransactionManager, JdbcTemplate jdbcTemplate) {
        super(platformTransactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }
    ...
```

Since we decided to use a managed projection, we extended the `AbstractSpringTxManagedProjection` class.
Transactionality is provided by the Spring [`PlatformTransactionManager`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/PlatformTransactionManager.html)
which is injected via the constructor. To register the `PlatformTransactionManager` in Factus, the parent constructor has to be invoked. 
To let the projection communicate with the external database, additionally a `JdbcTemplate` is injected.

Providing Required Methods
--------------------------

The two possible abstract base classes, `AbstractSpringTxManagedProjection` or `AbstractSpringTxSubscribedProjection`, 
both require the following methods to be implemented:

|   Method Signature                                 | Description |
|----------------------------------------------------|------------------------------|
|`public UUID state();`               | read the last position in the Fact stream from the database |
|`public void state(@NonNull UUID state);` | write the current position of the Fact stream to the database |
|`public WriterToken acquireWriteToken(@NonNull Duration maxWait)`   | TODO empty implementation - why ? no external global lock available? |

The first two methods tell Factus how to read and write the Fact stream's position 
(a single UUID value) from the database. Since this is a single value, you could get away with 
one table (e.g. `example_spring_tx_managed_projection_state`) possessing one UUID column.

However, to prepare for more than one transactional projection, we recommend the use of a single table
containing the Fact stream position of multiple projections:

```sql
CREATE TABLE projection (
    name varchar(255),
    state UUID, 
    PRIMARY KEY (name));
```


### Writing The Fact Position

Provided that the table `projection` exists, here is an example of how to write the Fact position: 

```java
@Override
public void state(@NonNull UUID state) {
    jdbcTemplate.update(
            "INSERT INTO projection (name, state) VALUES (?, ?) " +
             "ON CONFLICT (name) DO UPDATE SET state = ?",
            getScopedName().asString(),
            state,
            state);
}
``` 

For convenience an UPSERT statement (Postgres syntax) is used which INSERTs the UUID the first time 
and subsequently only UPDATEs the value. 

To avoid hard-coding a unique name for the projection, the helper method `getScopedName()` is used.
This method is inherited from the abstract base class.

For our previous example projection `ExampleSpringTxManagedProjection` the generated name is 
`package.path.to.projection.ExampleSpringTxManagedProjection_1`. 
The suffix `_1` indicates the serial number of the projection.      


### Reading The Fact Position

Building up on the previous example, here is how to read the Fact position: 

```java
@Override
public UUID state() {
    try {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM projection WHERE name = ?",
                UUID.class,
                getScopedName().asString());
    } catch (IncorrectResultSizeDataAccessException e) {
        // no state yet, just return null
        return null;
    }
}
``` 

In case no state exists yet, `null` is returned. 


Applying Events
----------------

As explained [in the introduction to transactional projections]({{< ref "application_transaction.md" >}})
when receiving an event, two write operations towards the relational database happen:

1. the projection is updated
2. the Fact stream position is persisted

The latter step was already discussed in the previous sections. 
What is left, is to actually handle the received events and update the projection.

As the concrete structure of your database tables depend on your use-case, here is an example projection which
handles *UserCreated* and *UserDeleted* events. The projection data is persisted in the table `users`:

```sql
CREATE TABLE users (
    name varchar(255), 
    id UUID, 
    PRIMARY KEY (id));
```

When processing the *UserCreated* event, we add a new row to the `users` tables, filled with event data: 

```java
@Handler
void apply(UserCreated e) {
    jdbcTemplate.update(
            "INSERT INTO users (name, id) VALUES (?,?);", 
            e.userName(), 
            e.aggregateId());
}
```

When handling the *UserDeleted* event we do the opposite and remove the appropriate row:

```java
@Handler
void apply(UserDeleted e) {
    jdbcTemplate.update("DELETE FROM users where id = ?", e.aggregateId());
}
``` 


### Tuning The Transaction

For fine-tuning the `@SpringTransactional` annotation provides two optional configuration parameters:

| Parameter Name   |  Description            | Default Value  |
|------------------|-------------------------|----------------|
| `size`           | local batch size        |  50            |
| `timeout`        | transaction timeout in seconds | 30      |


Full Example
------------

To study the full example see
- [the transactional projection code](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/proj/SpringTxMangedUserNames.java),
- [the logic working with this projection](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/SpringTxManagedUserNamesITest.java) and    
- [the Factus integration tests](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/SpringTransactionalITest.java) including managed- and subscribed projections.
