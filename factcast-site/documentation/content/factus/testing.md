+++
draft = false
title = "Testing"
description = ""
date = "2017-04-24T18:36:24+02:00"
weight = 99

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"

[menu.main]
parent = "usage"
identifier = "testing"

+++

//TODO needs to be updated


Factcast comes with a module 'factcast-test' that includes a Junit5 extension that you can use to modify namespaces dynamically during testing.
The idea is, that in integration tests, you may want to start every test method with no preexisting events. That would normally involve removing all events from factcast to create a clean fixture.
For obvious reasons, we hesitate in include a 'truncate-all-data' call into factcast. 

An alternative could be: Dynamically choosing a different namespace per test-method. In order to use this feature, you specify a namespace on your integration test events that ends with the character '$'.
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Specification(ns = "it-user$")
public class UserCreated implements EventObject {
    UUID aggregateId;

    String userName;

    @Override
    public Set<UUID> aggregateIds() {
        return Sets.newHashSet(aggregateId);
    }

}
```

At runtime of the test (given the FactCastExtension is active), the namespace used will be turned into 'it-user$00000001', where 1 is increased for every test method.
This way, every test method should be somewhat isolated in terms of events published.

In order for this to work, the FactCastExtension has to run. This can be achieved by either adding `@ExtendWith(FactCastExtension.class)` or by enabling automatic extension discovery in junit, which is done by adding a VM parameter: `-Djunit.jupiter.extensions.autodetection.enabled=true`.

  
