+++
draft = false
title = "Roadmap"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = ""
identifier = "roadmap"
weight = 190

+++

## Features planned

#### Integration of Schema Information

A directory of JSON-Schemas for the Facts published is planned, so that publishing a Fact required to publish the schema first (once, of course) for Consumers and FactCast itself to be able to validate incoming Facts.

#### Automatic Schema evolution

While subscribing to a Fact Stream, for every Fact type a consumer sends his preference on the Schema-version. Provided, that FactCast has code that transforms a Fact from Schema Version 1 to Schema Version 2 and back, it can automatically transform according to the consumers needs.
This takes decoupling a step furter, because a producer could be producing Version 2 Facts, where Consumers still receive them as Version 1. It also helps with canary releases on any side of the Stream.

#### Publish Commands along with Facts

There are situations, where an effect of a Command in one System involves not only observable Facts, but also Commands to other Systems. We often are in the situation, where you want them both to be published atomically. In order to make it easy, FactCast will support publishing Commands along with Facts, and use agents to reliably deliver them to external Systems (like SQS, or JMS for instance).

#### Spring MessageListenerContainer

Still in the early conceptual stages, it'd be nice to remove boilerplate from Fact-handling (like switching over types etc) and provide a Spring integration somewhat similar to the Models people are used to (Automatic unmarshalling to Java Types, etc...)

**And yes, you can help!**
