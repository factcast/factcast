+++
draft = false
title = "Anatomy of a Fact"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "intro"
identifier = "anatomy"
weight = 40
+++

## Facts

FactCast is centered around *Facts*. We say Facts instead of Events, because Event has become a blurry term that could mean any number of things from a simple ```onWhatNot() ``` call handled by an Event-Loop to a ```LegalContractCreated``` with any flavour of semantics.

We decided to use the term Fact over Domain-Event because we want to highlight the notion of an Event being an immutable thing that, once it is published, became an observable Fact.

Facts consist of two JSON documents: Header and Payload.

#### The Header 

consists of:

* a **required** Fact-Id 'id' of type UUID
* a **required** namespace 'ns' of type String
* an optional aggregateId 'aggId' of type UUID
* an optional (but mostly used) Fact-Type 'type' of type String
* an optional Object 'meta' any number of key-value pairs, where the values are Strings
* any additional information you want to put in a Fact Header

#### The Payload

has no constraints other than being a valid JSON document.

{{%alert danger%}} see REST docs / GRPC docs {{% /alert%}}