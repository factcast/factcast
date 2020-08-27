+++
draft = false
title = "Factus Setup"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus"
identifier = "factus-setup"
weight = 12

+++

First thing you need in your project is a dependency to factus itself.

```java
    <dependency>
      <groupId>org.factcast</groupId>
      <artifactId>factcast-factus</artifactId>
    </dependency>
```

If you use Spring-Boot and also have the spring boot autoconfiguration dependency included, 
```java
    <dependency>
      <groupId>org.factcast</groupId>
      <artifactId>factcast-spring-boot-autoconfigure</artifactId>
    </dependency>
```
this is all you need to get started.

However, there is a growing list of additional helpful dependencies when it comes to using Factus:

## Binary Snapshot Serializer

The default Snapshot Serializer in Factus uses Jackson to serialize to/from JSON. This might be less than optimal in terms of storage cost and transport performance/efficiency.
This optional dependency:

```java
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
