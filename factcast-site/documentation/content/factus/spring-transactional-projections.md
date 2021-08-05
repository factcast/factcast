+++
draft = false
title = "Spring Transactional Projection"
description = ""

creatordisplayname = "Maik Töpfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"

parent = "factus-transactional-projections"
identifier = "spring-transactional-projections"
weight = 1021

+++

--------------------

Spring comes with [extensive support for transactions](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction) 
which is employed by *Spring Transactional Projections*. 

Broad Data-Store Support
------------------------
Standing on the shoulders of [Spring Transactions](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction), 
Factus automatically supports transitionally for every data-stores for which Spring transaction management
is available. In more detail, for the data-store in question, an implementation of the Spring [`PlatformTransactionManager`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/PlatformTransactionManager.html)
must exist. Further down, we provide an example for JDBC.


Structure 
---------

Spring transactional projections are available for 
- [managed projections]({{< ref "managed-projection.md" >}})  and for 
- [subscribed projections]({{< ref "subscribed-projection.md" >}}).

Generally, such a projection has three ingredients:
- it is annotated with `@SpringTransactional`
- it extends either 
    - the class `AbstractSpringTxManagedProjection` or 
    - the class `AbstractSpringTxSubscribedProjection`
- it provides the serial number of the projection via the `@ProjectionMetaData` annotation

Here is an example:

```java
@ProjectionMetaData(serial = 1)
@SpringTransactional
public class GeneralSpringTransactionalProjectionExample extends AbstractSpringTxManagedProjection {

    public GeneralSpringTransactionalProjectionExample(
            @NonNull PlatformTransactionManager platformTransactionManager) {
        super(platformTransactionManager);
    }
    ...
```

Since we decided to use a managed projection, we extended the `AbstractSpringTxManagedProjection` class.
To configure transaction management, our managed projection exposes the injected transaction manager to the rest of Factus by calling the parent constructor. 

Based on this general approach, let's see how a Spring transactional projection with JDBC looks like:   


JDBC Based Projection
---------------------

In our example, we will create a JDBC based transactional projection which provides "user names":

{{<mermaid>}}
graph LR
    F[FactCast] -->|1. process events| P[UserNames Projection]
    C[Other Code] -->|2. uses| P[UserNames Projection] 
{{</mermaid>}}

First, we will focus on how to process events coming from the FactCast server. 
Then we will briefly show how the `UserNames` projection can be used by other code.  


### Constructing

We stick with the managed projection from above and, beside changing its name, we only extend it with one missing piece:

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
Additionally, to the previously explained `PlatformTransactionManager`, a `JdbcTemplate` is injected which allows 
simple communication with the database.

Two remarks:
1) As soon as your project uses the `spring-boot-starter-jdbc` dependency, 
   Spring Boot will [automatically provide](https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jdbc/DataSourceTransactionManagerAutoConfiguration.java) 
   you with a [JDBC-aware PlatformTransactionManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/support/JdbcTransactionManager.html).
2) To ensure that the database communication participates in the managed transaction, 
   the database access mechanism must be also provided by Spring. Thus, we suggest using the `JdbcTemplate`. 
      
      
### Providing Required Methods

The two possible abstract base classes, `AbstractSpringTxManagedProjection` or `AbstractSpringTxSubscribedProjection`, 
both require the following methods to be implemented:

|   Method Signature                                                | Description                                                 |
|-------------------------------------------------------------------|-------------------------------------------------------------|
|`public UUID factStreamPosition()   `                              | read the last position in the Fact stream from the database |
|`public void factStreamPosition(@NonNull UUID factStreamPosition)` | write the current position of the Fact stream to the database |
|`public WriterToken acquireWriteToken(@NonNull Duration maxWait)`  | coordinates write access to the projection, see [here]({{< ref "managed-projection.md" >}}) for details  |

The first two methods tell Factus how to read and write the Fact stream's position 
(a single UUID value) from the database. Since this is a single value, you could get away with 
one table (e.g. `spring_jdbc_transactional_projection_example_fact_stream_position` 😉  possessing one UUID column.

However, to prepare for more than one transactional projection, we recommend the use of a single table
containing the Fact stream position of multiple projections:
```sql
CREATE TABLE fact_stream_positions (
    projection_name TEXT,
    fact_stream_position UUID, 
    PRIMARY KEY (projection_name));
```


### Writing The Fact Position

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

For convenience an UPSERT statement (Postgres syntax) is used which INSERTs the UUID the first time 
and subsequently only UPDATEs the value. 

To avoid hard-coding a unique name for the projection, the helper method `getScopedName()` is employed.
This method is inherited from the abstract base class.

For our example projection `UserNames` the generated name is 
`package.path.to.projection.UserNames_1`. 
The suffix `_1` indicates the serial number of the projection.      


### Reading The Fact Position

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


### Applying Events

As explained [in the introduction to transactional projections]({{< ref "transactional-projections.md" >}}),
when receiving an event, two write operations towards the data-store (in our case a JDBC database) happen:

1. the projection is updated
2. the Fact stream position is persisted

The latter step was already discussed in the previous sections. 
What is left, is to actually handle the received events and update the projection.

As the concrete structure of your database tables depend on your use-case, here is an example projection which
handles *UserCreated* and *UserDeleted* events. The projection data is persisted in the table `users`:

```sql
CREATE TABLE users (
    name TEXT, 
    id UUID, 
    PRIMARY KEY (id));
```

When processing the *UserCreated* event, we add a new row to the `users` tables, filled with event data: 

```java
@Handler
void apply(UserCreated e) {
    jdbcTemplate.update(
            "INSERT INTO users (name, id) VALUES (?,?);", 
            e.getUserName(), 
            e.getAggregateId());
}
```

When handling the *UserDeleted* event we do the opposite and remove the appropriate row:

```java
@Handler
void apply(UserDeleted e) {
    jdbcTemplate.update("DELETE FROM users where id = ?", e.getAggregateId());
}
``` 

We have finished the implementation of the event-processing part of our projection. What is missing is a way to 
make the projection's data accessible for users.  


### Providing Access To The Projection

Users of our projections (meaning "other code") contact the projection via it's public API. 
Currently, there is no public method offering "user names". So let's change that:

```java
public List<String> getUserNames() {
    return jdbcTemplate.query("SELECT name FROM users", (rs, rowNum) -> rs.getString(1));
}
```

Once again, we employ the `JdbcTemplate` to provide the list of user names. Since our projection data is stored in 
a database, we simply use SQL to query the data.  

As the last step, we now change the perspective and look at how other code can use our `UserNames` projection.


### Using The Projection

Calling code which wants to talk to the projection, requires only a few changes: 

```java
// not needed when the projection is a Spring @Component
UserNames userNames = new UserNames(platformTransactionManager, jdbcTemplate);
factus.update(userNames);
List<String> userNames = uut.getUserNames();
```

First we create an instance of the projection and provide it with all required dependencies. Note, that in a Spring
application you would define the projection as `@Component` and let the dependency injection mechanism provide you an instance.

Next, we call `update(...)` on the projection to fetch the latest events from the Fact stream.  As last step, we ask 
the projection to provide us with user names by calling `getUserNames()`. 


Full Example
------------

To study the full example see
- [the UserNames projection using `@SpringTransactional`](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/proj/SpringJdbcTransactionalProjectionExample.java),
- [example code using this projection](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/SpringJdbcTransactionalProjectionExampleITest.java) and    
- [the Factus integration tests](https://github.com/factcast/factcast/blob/master/factcast-itests/factcast-itests-factus/src/test/java/org/factcast/itests/factus/SpringTransactionalITest.java) including managed- and subscribed projections.
