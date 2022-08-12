+++
title = "Java Optimistic Locking"
type="docs"
weight = 90
+++

### Motivation

Whatever your particular way of modelling your software, in order to be able to enforce invariants in your aggregates, you'd need to coordinate writes to it. In simple monoliths, you do that by synchronizing write access to the aggregate. When Software-Systems become distributed (or at least replicated), this coordination obviously needs to be externalized.

#### Pessimistic Locking

While pessimistic locking makes sure every change is strictly serializable, it has obvious drawbacks in terms of throughput and complexity (timeouts) as well as the danger of deadlock, when the scope of the lock expands to more than one aggregate. This is why we chose to implement a means of optimistic locking in FactCast.

#### Optimistic Locking

In general, the idea of optimistic locking is to make a change and before publishing it, make sure there was no potentially contradicting change in between. If there was, the process can safely be retried, as there was nothing published yet.

Transferred to FactCast, this means to express a body of code that:

1. creates an understanding of the published state of the aggregates in question
2. invokes its business logic according to that state
3. creates the effects: either fails (if business logic decides to do so), or publishes new Fact(s)
4. rechecks, if the state recorded in 1. is still unchanged and then
5. either publishes the prepared Facts or retries by going back to 1.

### Usage 

#### a simple example

This code checks if an account with id *newAccountId* already exists, and if not - creates it by publishing the Fact accordingly.

```java
factcast.lock("myBankNamespace")
        .on(newAccountId)
        .attempt(() -> {
            // check and maybe abort
            if (repo.findById(newAccountId) !=null)
                return Attempt.abort("Already exists.");
            else
              return Attempt.publish(
                Fact.builder()
                .ns("myBankNamespace")
                .type("AccountCreated")
                .aggId(newAccountId)
                .build("{...}")
              );            
        });

```

You may probably guess what happens, remembering the above steps. Let's dive into details with a more complex scenario.

#### a complete example

The unavoidable imaginary example, of two BankAccounts and a money transfer between them:

```java
factcast.lock("myBankNamespace")
        .on(sourceAccountId,targetAccountId)
        .optimistic()            // this is optional, defaults to optimistic, currently the only mode supported
        .retry(100)              // this is optional, default is 10 times
        .interval(5)             // this is optional, default is no wait interval between attempts (equals to 0)
        .attempt(() -> {
            
            // fetch the latest state
            Account source = repo.findById(sourceAccountId);
            Account target = repo.findById(targetAccountId);
            
            // run businesslogic on it
            if (source.amount() < amountToTransfer)
                return Attempt.abort("Insufficient funds.");
            
            if (target.isClosed())
                return Attempt.abort("Target account is closed");
            
            // everything looks fine, create the Fact to be published
            Fact toPublish = Fact.builder()
                .ns("myBankNamespace")
                .type("transfer")
                .aggId(sourceAccountId)
                .aggId(targetAccountId)
                .build("{...}");            
            
            // register for publishing
            return Attempt.publish(toPublish).andThen(()->{
                
                // this is only executed at max once, and only if publishing succeeded
                log.info("Money was transferred.");
                
            });
        });
```

#### Explanation

First, you tell factcast to record a state according to all events that have either *sourceAccountId* or *targetAccountId* in their list of aggIds and are on namespace *myBankNamespace*. While the namespace is not strictly necessary, it is encouraged to use it - but it depends on your decision on how to use namespaces and group Facts within them.

The number of retries is set to *100* here (default is 10, which for many systems is an acceptable default). In essence this means, that the attempt will be executed at max 100 times, before factcast gives up and throws an `OptimisticRetriesExceededException` which extends `ConcurrentModificationException`.

If *interval* is not set, it defaults to 0 with the effect, that the code passed into *attempt* is continuously retried without any pause until it either *aborts*, succeeds, or the max number of retries was hit (see above).
Setting it to *5* means, that before retrying, a 5 msec wait happens. 

{{< warning >}}<b>WARNING</b>: Setting interval to non-zero makes your code block a thread. The above combination of 100 retries with a 5 msec interval means, that - at worst - your code could block <i>longer than half a second</i>.{{< /warning >}}


Everything starts with passing a lambda to the *attempt* method. The lambda is of type 
```java
@FunctionalInterface
public interface Attempt {
    IntermediatePublishResult call() throws AttemptAbortedException;
    //...
}
```
so that it has to return an instance of `IntermediatePublishResult`. The only way to create such an instance are static methods on the same interface (`abort`, `publish`, ...) in order to make it obvious.
This lambda now is called according to the logic above.

Inside the lambda, you'd want to check the current state using the very latest facts from factcast (`repo.findById(...)`) and then check your business constraints on it (`if (source.amount() < amountToTransfer)`...).
If the constraints do not hold, you may choose to abort the Attempt and thus abort the process. In this case, the attempt will **not** be retried.

On the other hand, if you choose to publish new facts using `Attempt.publish(...)`, the state will be checked and the Fact(s) will be published if there was not change in between (otherwise a retry will be issued, see above).
In the rare case, that you do not want to publish anything, you can return `Attempt.withoutPublication()` to accomplish this.

*Optionally*, you can pass a runnable using `.andThen` and schedule it for execution once, if **and only if** the publishing succeeded. Or in other words, this runnable is executed just once or never (in case of *abort* or *OptimisticRetriesExceededException*).


