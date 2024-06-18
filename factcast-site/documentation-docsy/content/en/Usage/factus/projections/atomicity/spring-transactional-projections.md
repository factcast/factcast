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

You can find blueprints of getting started in the [example section](/usage/factus/projections/example).
