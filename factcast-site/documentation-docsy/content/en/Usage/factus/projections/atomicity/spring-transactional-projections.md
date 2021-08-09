+++
title = "Spring Transactional Projection"
weight = 20
type="docs"
+++



## Broad Data-Store Support

Spring comes with [extensive support for transactions](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction)
which is employed by *Spring Transactional Projections*.

Standing on the shoulders of [Spring Transactions](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction), 
Factus supports transactionality for every data-store for which Spring transaction management
is available. In more detail, for the data-store in question, an implementation of the Spring [`PlatformTransactionManager`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/PlatformTransactionManager.html)
must exist. 

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

In your @Handler methods, you need to make sure, you use the Spring-Managed Transaction when talking to your datastore. 
This might be completely transparent to you (for instance when using JDBC that assigns the transaction to the current thread), or will need you to resolve the current transaction from the given `platformTransactionManager` [example](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#148-spring-transaction-manager).

Please consult the spring docs or your driver's documentation.

{{% alert  title="Note" %}} 

Factus provides convenient abstract classes for managed and subscribed projections:
 - `AbstractSpringTxManagedProjection`
 - `AbstractSpringTxSubscribedProjection`

{{% / alert %}}

You can find blueprints of how to get started in the [example section](/usage/factus/projections/example).
