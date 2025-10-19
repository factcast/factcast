# Virtual Threads support for Factus

## The problem

Factus uses an implementation of `InLockedOperation` to verify that inside of a transaction,
you use the transaction to publish events, and not `factus.publish`.

This implementation uses ThreadLocal, which does not work any more when run on virtual threads.

## The solution
A new implementation for virtual threads is provided in this package (but cannot be delivered
with the factus standard package, as it requires JDK 25).

In case you use [factcast-spring-boot-autoconfigure](../factcast-spring-boot-autoconfigure), all
you need to do is additionally adding `factcast-factus-jdk25` to your dependencies.

If you create the factus instance yourself, make you use the newest version that allows you to
specify the `InLockedOperation` in the constructor of `FactusImpl`, and use
`InLockedOperationForVirtualThreads`.
