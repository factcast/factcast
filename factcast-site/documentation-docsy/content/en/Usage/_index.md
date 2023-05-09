---

title: "API"
menu:
main:
weight: 60
type: docs

## weight: 40

## Choose your weapon: FactCast vs Factus

One main design goal of FactCast is to be non-intrusive. That is supposed to mean, that tries to impose as little
constraints on the client as possible and thus does not make too many assumptions about how exactly Facts are generated
or processed. Facts - as you remember - are just tuples of JSON-Strings (header & payload) that everyone can use the way
he/she likes.

However, that focus on freedom sometimes makes it hard for application programmers to know where to start, or how to
implement good practices like for instance the different kinds of models, locking or even just generating Facts from
Java Objects.

#### This is where Factus comes in.

Factus is a higher-level API for java applications to work with factcast. Using Factus from java is entirely optional.
Factus just uses FactCast underneath, so every feature you may find in Factus can be used with raw FactCast as well.

> Whereas factcast tries to limit the number of assumptions, Factus is highly opinionated.

Factus provides higher level abstractions that are supposed to make it faster and more convenient to work with FactCast
from Java. For an Overview about what Factus can do, see the [Factus](/usage/factus) section.
