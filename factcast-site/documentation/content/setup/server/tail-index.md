+++
title = "Tail Index"

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
However, as the fact log is constantly growing, so is the index. 
With the global index alone, query performance decreases.


### Subscription Phases

Generally, a [subscription]({{< ref "concept/_index.md#read-subscribe" >}}) consists of two phases:
1. Catching up with past events, starting from the beginning of the fact log
2. Checking for new events by querying the tail of the fact log

While 1.) is happening only once at the beginning of a subscription, 2.) is happening regularly. 


Tail Index
----------

A *tail index* supports the regular "Are there new events?" queries by placing one or more indexes 
at *the end* of the fact log:

![](../tail-index.png)

More precisely, FactCast maintains a certain number of rolling tail indexes.
When asked to query facts from the end of the fact log, 
the Postgres database has now multiple options. Besides the large global index, there are now much smaller ones
that most likely cover the query and are cheaper to access.  


Tail index rotation is configurable and described in [the configuration properties]({{< ref "properties.md#performance--reliability" >}}).


Note: Tail indexes are implemented as [Postgres Partial Indexes](https://www.postgresql.org/docs/11/indexes-partial.html). 


Index Maintenance Trade-Off
---------------------------
Introducing a new index does not come for free. When new facts are INSERTed, the Postgres database needs to maintain
the indexes of the fact log.  Hence, the higher the number of indexes, the slower the INSERT performance. 
See the [recommendations of the configuration section]({{< ref "properties.md#performance--reliability" >}}) for sensible values 
on the number of tail index generations. 

 A tail index is a Postgres GIN index with enabled [fastupdate](https://www.postgresql.org/docs/11/sql-createindex.html).
 If you encounter performance issues, see the [Postgres documentation](https://www.postgresql.org/docs/11/gin-implementation.html#GIN-FAST-UPDATE) for further advice.


Fast-Forward
------------
The Fast-Forward feature further improves the effectiveness of tail indexes by pushing a client's fact stream position to the end of the fact stream. 
There, checking for new facts is supported by the tail indexes.    

Based on the fact log diagram above, here is an example of how a regular check for new events without Fast-Forward works:  

{{< mermaid>}}
sequenceDiagram
    FactCast Client->>FactCast Server: Are there new facts after position 10?
    Note right of FactCast Server: look for new facts <br/> via the Global Index
    FactCast Server->>FactCast Client: No, nothing new
    Note over FactCast Client,FactCast Server: After some time...
    FactCast Client->>FactCast Server: Are there new facts after position 10?
    Note right of FactCast Server: look for new facts <br/> via the Global Index
    FactCast Server->>FactCast Client: ...
{{</ mermaid>}}

The client asks the server for new events after its current position, "10". Since
this position is not at the tail of the fact log anymore, the FactCast database has to use the expensive Global index
to check for new facts. As there are no recent events, the fact stream position stays at "10", and after a while, 
the same expensive query via the Global index is repeated.

With Fast-Forward the situation is different:

{{< mermaid>}}
sequenceDiagram
    FactCast Client->>FactCast Server: Are there new facts after position 10?
    Note right of FactCast Server: look for new facts via <br/> the Global Index
    FactCast Server->>FactCast Client: No, nothing new. Your new fact stream position is 500000 
    Note over FactCast Client,FactCast Server: After some time...
    FactCast Client->>FactCast Server: Are there new facts after position 500000?
    Note right of FactCast Server: look for new facts <br/> via Tail Index no. 1
    FactCast Server->>FactCast Client: ...
{{< /mermaid>}}

Here, the client still asks the server for new events after its current position "10". Again,
the FactCast database has to use the Global Index. However, besides informing that no new events were found,
the client is fast-forwarded to position "500000" in the fact stream. Looking at the diagram of the fact log above, we see that position
"500000" is the beginning of the most recent tail index #1. On its next call, the client uses this position as the start of the fact stream.
Since this position is covered by a tail index, FactCast can scan much quicker for new events than before. 

Fast-Forward can be imagined like a magnet on the right hand, tail side of the fast stream: Whenever possible, 
FactCast tries to drag clients from a behind position to the tail of the fact stream.

Notes:
- To omit unnecessary writes of the fact stream position on the client-side, FactCast always offers the beginning of
  the tail index to the client.
- Fast-Forward is a client- and server-side feature of FactCast 0.4.0 onward. However, older clients remain compatible
with a newer FactCast server as the additional Fast-Forward notification is ignored. 
   