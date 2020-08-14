+++
draft = false
title = "Introduction"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus"
identifier = "factus-intro"
weight = 1

+++

## Motivation

If Factus is optional, why does it exist in the first place, you might ask.

FactCast tries to be non-intrusive. It focuses on publishing, retrieval, validation and transformation of json documents. It also provides some tools for advanced (yet necessary) concepts like optimistic locking, but it does not prescribe anything in terms of how to use this to build an application.

Depending on your experience with eventsourcing in general or other products/approaches in particular, it might be hard to see, how exactly this helps you to build correct, scalable and maintainable systems. At least this was our experience working with a very diverse group of engineers over the years.

Now, instead of documenting lots of good practices here, we thought it would be easier to start with, more convenient and less error prone to offer a high-level API instead, that codifies those good practices.

We say **"good" practices** here, rather than *"best" practices* for a reason. Factus represents just one way of using FactCast from java. Please be aware that it may grow over time and there is nothing wrong with using a different approach.
Also, be aware that not every possible usecase is covered by Factus so that occasionally, you might want to fall back to "doing things yourself" with the low-level FactCast API. 
In case you encounter such a situation, please open a github issue explaining your motivation. Maybe this is something Factus is currently lacking.

## Enough already, give me details!

Factus replaces FactCast as a central interface. Rather than with 'Facts', Factus deals with 'EventObject's. Those with be serialized/deserialized to Facts using an 'EventSerializer'.
Factus ships with a default one that uses Jackson, but your free to use any lib of your taste to accomplish this (like gson, or whatever is popular with you).

Concrete Events will implement 'EventObject' in order to be able to contribute to Fact Headers when serialized, and they are expected to be annotated with '@Specification' in order to specify what the specifics of the FactHeader (namespace, type and version) are.

```java
/**
 * EventObjects are expected to be annotated with @{@link Specification}.
 */
public interface EventObject {

    default Map<String, String> additionalFactHeaders() {
        return Collections.emptyMap();
    }

    Set<UUID> aggregateIds();

}
```

Now the payload of a Fact created from your Events will be, as you'd expect, the json-serialized form of the Event which is created by the 'EventSerializer'.

So. now that we covered Facts and Events and their conversion, what to do with them? 

