+++
draft = false
title = "Projections"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus"
identifier = "factus-projections"
weight = 5

+++

Before we can look at processing Events, we first have to talk about another abstraction that does not exist in FactCast: `Projection``` 


```java
public interface Projection { ... }
```

In Factus, a Projection is any kind of state that is distilled from processing Events - in other words: `Projection`s process (or handle) events.

There are several kinds of Projections that we need to look at, but here is an overview:

![](../projections.png)
