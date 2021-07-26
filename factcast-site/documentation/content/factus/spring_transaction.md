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

TODO mention Postgres here - probably not. It's DB independent 
TODO wording - "Fact position" instead of state
TODO are there Spring transactional subscribed projs possible? should we document them as well ?

A Spring based managed projection has three ingredients:
- it is annotated with `@SpringTransactional`
- it extends the abstract class `AbstractSpringTxManagedProjection`
- it provides the serial number of the projection via the `@ProjectionMetaData` annotation

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

Transactionality is provided by the Spring [`PlatformTransactionManager`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/PlatformTransactionManager.html)
and is injected via the constructor. To register the `PlatformTransactionManager` for Factus, the parent constructor has to be invoked.

To let the managed projection communicate with the external database, additionally a `JdbcTemplate` is injected.


Providing Required Methods
--------------------------

The parent class `AbstractSpringTxManagedProjection` requires the following methods to be implemented:

|   Method Signature                                 | Description |
|----------------------------------------------------|------------------------------|
|`public UUID state();`| read the last position in the Fact stream from the database |
|`public void state(@NonNull UUID state);` | write the current position of the Fact stream to the database |
|`public WriterToken acquireWriteToken(@NonNull Duration maxWait)`| TODO empty implementation -  (only used for subscribed projections?)|

The first two methods tell Factus how to read and write the Fact stream's position 
(a single UUID value) from the database. Since this is a single value you could get away with 
one table (e.g. `example_spring_tx_managed_projection_state`) possessing one UUID column.

However, to prepare for more than one transactional projection, we recommend the use of a single table
containing the Fact stream position of multiple projections:

```sql
CREATE TABLE managed_projection (
    name varchar(255),
    state UUID, 
    PRIMARY KEY (name));
```


### Writing The Fact Position

Provided that the table `managed_projection` exists, here is an example of how to write the Fact position: 

```java
@Override
public void state(@NonNull UUID state) {
    jdbcTemplate.update(
            "INSERT INTO managed_projection (name, state) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET state = ?",
            getScopedName().asString(),
            state,
            state);
}
``` 



For convenience an UPSERT statement (Postgres syntax) is used which INSERTs the UUID the first time 
and subsequently only UPDATEs the value. 

To avoid hard-coding a unique name for the table `managed_projection`, the helper method `getScopedName()` is used.
This method is inherited from the base class `AbstractSpringTxManagedProjection` and for 
our previous example projection `ExampleSpringTxManagedProjection` would generate the name 
`package.path.to.projection.ExampleSpringTxManagedProjection_1`. The suffix `_1` indicates 
the serial number of the projection.      

  


### Reading The Fact Position

```java
    @Override
    public UUID state() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT state FROM managed_projection WHERE name = ?",
                    UUID.class,
                    getScopedName().asString());
        } catch (IncorrectResultSizeDataAccessException e) {
            // no state yet, just return null
            return null;
        }
    }

    @Override
    public void state(@NonNull UUID state) {
        jdbcTemplate.update(
                "INSERT INTO managed_projection (name, state) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET state = ?",
                getScopedName().asString(),
                state,
                state);
    }
``` 


 

This could be a single table with just one UUID column - 
however, to allow for multi

### Storing The Stream Position In The Database




### Reading The Latest Position

Reading and writing the Fact stream position requires one   






TODO: I don't see a bean providing the PlatformTransactionManager 

- only managed projections ?
