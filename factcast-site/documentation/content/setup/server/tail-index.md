+++
draft = false
title = "Tail Index"
description = ""

creatordisplayname = "Maik Toepfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"

parent = "usage"
identifier = "tail-index"
weight = 220
+++

The *tail index* is a performance optimization for FactCast which speeds up queries 
against the end (the *tail*) of the fact log.

Background
----------

### Global Index

FactCast uses a Postgres database for its persistence. Events (or *Facts*) live in a single database table
called *fact* which is referred to as the *fact log*. To speed up access to the fact log, 
a [global index](https://www.postgresql.org/docs/11/textsearch-indexes.html) is used. 
However, as the fact log is always growing, so is the index (good English?). 
With the global index alone, query performance decreases.


### Subscription Phases

Generally, a [subscription]({{< ref "concept/_index.md#read-subscribe" >}}) consists of two phases:
1. Catching up with past events, starting from the beginning of the fact log
2. Checking for new events by querying the tail of the fact log

While 1.) is happening only once at the beginning of a subscription, 2.) is happening regularly. 
For example, a [Factus managed projection]({{< ref "managed-projection.md">}}) is updated via a call to `factus.update(myProjection)`.
The first call is expensive as it is catching up with all past events (1.). Subsequent calls of `update()`
only ask for new events at the tail of the fact log (2.)   


Tail Index
----------

A *tail index* supports the regular "Are there new events?" queries by placing one or more indexes 
at *the end* of the fact log:

![](../tail-index.png)

More precisely, FactCast maintains a certain number of rolling tail indexes. Once an index becomes too old,
FactCast removes it and creates a new smaller one. When asked to query facts from the end of the fact log, 
the Postgres database has now multiple options. Beside the large global index, there are now much smaller ones
which most likely cover the query and are cheaper to access.  

Note: Tail indexes are implemented as [Postgres Partial Indexes](https://www.postgresql.org/docs/11/indexes-partial.html). 


Index Maintenance Trade-Off
---------------------------
Introducing a new index does not come for free. When new facts are INSERTed, the Postgres database needs to maintain
the indexes of the fact log.  Hence, the higher the number of indexes, the slower the INSERT performance. 
See the [recommendations of the configuration section]({{< ref "properties.md#performance--reliability" >}}) for sensible values 
on the number of tail index generations. 



