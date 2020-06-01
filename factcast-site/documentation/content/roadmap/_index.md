+++
draft = false
title = "Roadmap"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = ""
identifier = "roadmap"
weight = 100

+++

## Features planned

#### GraalVM Support

First priority is to provide support for JavaScript execution on GraalVM, as the Nashorn (the engine currently in use) was sadly deprecated. A second priority is to enable factcast Server to be packaged as a native image so that it uses less memory and save some money this way. The ability to start fast is nice, but as FactCast is probably not reasonable to run on demand, it is less significant.
Fear not: we will be careful to enable GraalVM usage on the FactCast server without leaking down to the client.

For tools like factcast-cli and schema-registry-cli however, starting fast and self-contained would be a huge gain. 

#### Kotlin client wrapper

We want to provide a Kotlin wrapper against the client library, so that we can make better use of Kotlins abilities to provide more fluent and idiomatic APIs.
One simple example can be the use of optimistic locks, that can be greatly expressend in kotlin's syntactic sugar of passing a lambda as the last param.

#### Spring MessageListenerContainer

Still in the early conceptual stages, it'd be nice to remove boilerplate from Fact-handling (like switching over types etc) and provide a Spring integration somewhat similar to the Models people are used to (Automatic unmarshalling to Java Types, etc...)

#### Snapshotting facility

When implementing event-consumers, there is a need to persist and read snapshots of projections alongside with their 'latest relevant' event-id. These snapshots have to be compacted, managed etc. While FactCast is not the best place to store those, it often may be overkill to introduce the extra complexity of additional infrasructure (like for instance a redis server) to every service where this is needed.
Therefor, FactCast should offer a simple API to do that, that can be either implemented at the client (by for instance pushing these into redis), or at the FactCast Server, that can either push the data down to postgres, or use a pluggable backend for those snapshots himself.  

Once again, this does not necessarily belong into FactCast, so it might end up to be a separate project, that FactCast just happens to implement.

## Internal changes planned

#### Github Actions

Moving from cirlceCI to github actions has to move soon, in order to provide CI, static code-analysis etc, without being stuck exceeding a free-plan limit.

#### Gradle

Gradle provides a few optimizations, that reduce the roundtrip time of local builds. Also the destinction of API dependency vs. Implementation dependency is very interesting. Therefore, we'd like to experiment with this non-trivial project, if a migration is possible at this point in time.
One thing that has to be done before is to move from dependabot to renovate, as dependabot is not able to read gradle's .kts files.

#### Restructure modules

In the very beginning this project was intended to support a rich variety of datastores. We early on concentrated on postgres and dropped a strict separation of core functionality and store implementation, as we heavily rely on some unique features of postgres by now. This might also be reflected by property name changes, that will be properly documented in the migration guide (promise).
  


**And yes, you can help!**


