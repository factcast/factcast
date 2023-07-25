+++
title = "Introduction"
weight = 10
type = "docs"
+++

### Motivation

If Factus is optional, why does it exist in the first place, you might ask.

FactCast tries to be non-intrusive. It focuses on publishing, retrieval, validation and transformation of JSON documents. It also provides some tools for advanced (yet necessary) concepts like optimistic locking, but it does not prescribe anything in terms of how to use this to build an application.

Depending on your experience with eventsourcing in general or other products/approaches in particular, it might be hard to see how exactly this helps you to build correct, scalable and maintainable systems. At least this was our experience working with diverse groups of engineers over the years.

Now, instead of documenting lots of good practices here, we thought it would be easier to start with, more convenient and less error-prone to offer a high-level API instead, that codifies those good practices.

We say **"good" practices** here, rather than _"best" practices_ for a reason. Factus represents just one way of using FactCast from Java. Please be aware that it may grow over time and that there is nothing wrong with using a different approach.
Also, be aware that not every possible use case is covered by Factus so that you occasionally might want to fall back to "doing things yourself" with the low-level FactCast API.
In case you encounter such a situation, please open a GitHub issue explaining your motivation. Maybe this is something Factus is currently lacking.

### Factus as a higher level of abstraction

Factus replaces FactCast as a central interface. Rather than with _Facts_, Factus primarily deals with _EventObjects_ deserialized from Facts.
using an _EventSerializer_. Factus ships with a default one that uses Jackson, but you're free to use any library of your taste to accomplish this (like Gson, or whatever is popular with you).

Concrete events will implement _EventObject_ in order to be able to contribute to Fact Headers when serialized, and they are expected to be annotated with `@Specification` in order to declare what the specifics of the `FactHeader` (namespace, type and version) are.

```java
import com.google.common.collect.Sets;

/**
 * EventObjects are expected to be annotated with @{@link Specification}.
 */
public interface EventObject {

  default Map<String, String> additionalFactHeaders() {
    return Collections.emptyMap();
  }

  Set<UUID> aggregateIds();

}

/**
 * Example EventObject based event containing one property
 */
@Specification(ns = "user", type = "UserCreated", version = 1)
class UserCreated implements EventObject {

  // getters & setters or builders omitted
  private UUID userId;
  private String name;

  @Override
  public Set<UUID> aggregateIds() {
    return Sets.newHashSet(userId);
  }
}
```

Now the payload of a Fact created from your Events will be, as you'd expect, the json-serialized form of the Event which is created by the `EventSerializer`.

Factus ships with a default serializer for EventObjects. It uses Jackson and builds on a predefined ObjectMapper, if defined (otherwise just uses the internal FactCast-configured ObjectMapper).
If, for some reason, you want to redefine this, you can use/ provide your own EventSerializer.

As factus is optional, you'll first want to setup you project to use it. See [Factus Setup](../setup)
