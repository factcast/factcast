+++
draft = false
title = "Technical Design"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "intro"
identifier = "techchoices"
weight = 30
+++

## Technical requirements

Base on the formentioned requirements, let us highlight some technical choices.

## Choice #1: PostgreSQL for persistence

Some Reasons for choosing PostgreSQL as persistence layer for the Events:

#### Serializability / Atomicity

Both technical requirements are trivial when choosing a RDBMS, due to its ACID nature and the availability of Sequences.

#### Familiarity

Monitoring, Alerting, Backup, Point in time recovery, Authentication / Authorization, read-replication, fail-over ... All of those are properties of a good RDBMS and it is hard to find more mature solutions than the ones we can find there.

#### Flexible Querys

While Document datastores like MongoDB certainly have more to offer here, PostgreSQL is surprisingly good with JSON. FactCast uses GIN Indexes on JSONB Columns in order to find matching Facts for subscriptions easily.

#### Coordination

With ```LISTEN``` and ```NOTIFY``` PostgreSQL makes the transition from a passive, queryable Datastore to a reactive one, that can be used to guarantee low latency pushes of new Facts down to subscribers, irrelevant at which instance of FactCast the write has happened, without the need of any further message-bus/topic/whatever.

#### Read-Replicas

A basically solved problem.

#### Cloud-ready

With AWS RDS for instance, it is rather trivial to setup and operate a PostgreSQL that satisfies the above needs.

## Choice #2: REST

## Choice #2: GRPC


