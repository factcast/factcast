+++
title = "Roadmap"
weight = 100020
type = "docs"
+++

## Features planned

#### GraalVM Support

First priority is to provide support for JavaScript execution on GraalVM, as the Nashorn (the engine currently in use) was sadly deprecated. A second priority is to enable factcast Server to be packaged as a native image so that it uses less memory and save some money this way. The ability to start fast is nice, but as FactCast is probably not reasonable to run on demand, it is less significant.
Fear not: we will be careful to enable GraalVM usage on the FactCast server without leaking down to the client.

For tools like factcast-cli and schema-registry-cli however, starting fast and self-contained would be a huge gain. 

#### Kotlin client wrapper

We want to provide a Kotlin wrapper against the client library, so that we can make better use of Kotlins abilities to provide more fluent and idiomatic APIs.
One simple example can be the use of optimistic locks, that can be greatly expressend in kotlin's syntactic sugar of passing a lambda as the last param.

## Internal changes planned

#### Restructure modules

In the very beginning this project was intended to support a rich variety of datastores. We early on concentrated on postgres and dropped a strict separation of core functionality and store implementation, as we heavily rely on some unique features of postgres by now. This might also be reflected by property name changes, that will be properly documented in the migration guide (promise).
  


**And yes, you can help!**


