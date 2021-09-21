---
title: "Hitchhiker's Guide To Testing"
weight: 10
type: docs
---
## General Test Approach

An event sourced application usually performs [two kinds of interaction]({{< ref "/concept">}}) with the FactCast server:
- It subscribes to Facts and builds up use-case specific views of the received data. These use-case specific views are called *projections*.   
- It writes new Facts to the event log.

{{% alert info %}}
Building up *projections* works on both API levels, low-level and Factus. 
However, to simplify development, the high-level Factus API has [explicit support for this concept]({{< ref "/usage/factus/projections/types">}}).
{{% /alert %}}
 
### Unit Tests

*Projections* are best tested in isolation, ideally at unit test level. 
At the end they are classes receiving facts and updating some internal state. 
As we will see in a moment, a unit test is a perfect fit here. 
However, as soon as the projection's state is externalized (e.g. [see here]({{< ref "/usage/factus/projections/atomicity/" >}})) this test approach can get challenging. 

### Integration Tests

Integration tests check the interaction of more than one component (usually a class) - hence the name. 
For example, you would use an integration test to confirm that a request at the REST layer really led to an event being published. 
As mentioned in the previous section, integration tests also help to validate the correct behaviour of
a projection which uses external state like a Postgres database.

{{% alert warn %}}
Be aware that FactCast integration tests startup real infrastructure via Docker. 
For this reason they are *a magnitude slower than unit tests*.
{{% /alert %}}

----
# Building Site - No Trespassing




## Testing with Factus

Factus builds up on the low-level FactCast API and provides a higher level of abstraction.  
As with the direct Factcast API described before 

TODO: show Redis Map example

- challenges: state external like Redis or Postgres: Try to 
