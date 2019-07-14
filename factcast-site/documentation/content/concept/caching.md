+++
draft = false
title = "Caching Facts"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"

[menu.main]
parent = "concept"
identifier = "caching"
weight = 30
+++

## Caching Facts

As mentioned before Facts are supposed to be conceptually immutable. For that reason, they can be persistently cached forever, once retrieved.

For that reason, while subscribing to streams of Facts, you can choose to be either streamed the matching Facts, or just their IDs, used to fetch the Events (maybe from a cache) on the side. As Facts are inherently immutable and not directed to a particular consumer, consumers could share this cache easily, thus profit from another and reducing the load on the FactCast-Server(s) itself. 

#### Beware of optimizations

While it *looks* like an obvious no-brainer to use caching in one form or another, think twice about the context your application lives in. Depending on the Transport Protocol used, the overhead of fetching single Facts and maintaining a local cache is considerable.

Also the Scope of the cache is a relevant parameter: If you have 3 consumers that share a cache on an ephemeral local disk, that might not be worth it. And then, there are many different usage patterns: many small Events at a very high rate / few huge Events at moderate rates... All these things should be considered and tested with realistic workloads before you decide, if and how you want to cache Facts, or not.

If your Application lives in the same physical network than FactCast, it might turn out to be not worth it. *You have been warned.*

 

