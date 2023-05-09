+++
title = "Setup"
weight = 12
type="docs"
+++

### Dependencies

First thing you need in your project is a dependency to factus itself.

```xml
<dependency>
  <groupId>org.factcast</groupId>
  <artifactId>factcast-factus</artifactId>
</dependency>
```

If you use Spring-Boot and also have the spring boot autoconfiguration dependency included,

```xml
<dependency>
  <groupId>org.factcast</groupId>
  <artifactId>factcast-spring-boot-autoconfigure</artifactId>
</dependency>
```

this is all you need to get started.

However, there is a growing list of optional helpful dependencies when it comes to using Factus:

---

### Binary Snapshot Serializer

The default Snapshot Serializer in Factus uses Jackson to serialize to/from JSON. This might be less than optimal in terms of storage cost and transport performance/efficiency.
This optional dependency:

```xml
<dependency>
  <groupId>org.factcast</groupId>
  <artifactId>factcast-factus-bin-snapser</artifactId>
</dependency>
```

replaces the default Snapshot Serializer by another variant, that - while still using jackson to stay compatible
with the default one from the classes perspective - serializes to a binary format and uses lz4 to swiftly (de-)compress
it on the fly.

Depending on your environment, you may want to roll your own and use a slower, but more compact compression or maybe
just want to switch to plain Java Serialization. In this case, have a look at `BinarySnapshotSerializer` to explore, how to do it.
(If you do, please contribute it back - might be worthwhile integrating into factcast)

Should be straightforward and easy.

In case you want to configure this serializer, define a `BinaryJacksonSnapshotSerializerCustomizer` bean and
define the configuration in there. Take a look at `BinaryJacksonSnapshotSerializerCustomizer#defaultCustomizer`
if you need inspiration.

---

### Redis SnapshotCache

From a client's perspective, it is nice to be able to persist snapshots directly into factcast, so that you dont
need any additional infrastructure to get started. In busy applications with many clients however, it might be
a good idea to keep that load away from factcast, so that it can use its capacity to deal with Facts only.

In this case you want to use a different implementation of the SnapshotCache interface on a client, in order to
persist snapshots in your favorite K/V store, Document Database, etc.

We chose Redis as an example database for externalized shared data for the examples, as it has a very simple API and is
far more lightweight to use than a RDBMS. **But, please be aware, that you can use _ANY_ Database to store shared data
and snapshots**, by just implementing the respective interfaces.

In case Redis is you weapon of choice, there is a Redis implementation of that interface. Just add

```xml
<dependency>
  <groupId>org.factcast</groupId>
  <artifactId>factcast-snapshotcache-redisson</artifactId>
</dependency>
```

to your client's project and spring autoconfiguration (if you use spring boot) will do the rest.

As it relies on the excellent [Reddison](https://redisson.org/) library, all you need is to add the corresponding redis configuration to your project.
See [the Redisson documentation](https://github.com/redisson/redisson/tree/master/redisson-spring-boot-starter).
