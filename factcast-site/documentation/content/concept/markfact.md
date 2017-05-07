+++
draft = false
title = "MarkFact"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "concept"
identifier = "markfact"
weight = 90
+++

## The thing about MarkFacts

When you publish Facts to FactCast, you have no idea of the position in the log they end up in. It is guaranteed, that Facts are cast in the order they were published, but as a consumer, you cannot tell where in the Stream you are, other than by remembering the id of the last Fact consumed.

Now consider a *CQRS* System, where your write model publishes changed that will *eventually* be consumed by your read model. While this is fine in most cases, some cases make coordination necessary. If, for instance, you want to write a perceived synchronous interface, where you have a list view with a Customers ID and Name, and a detail view, where you can change the name.

#### Synchronous UIs

If you changed the name and go back to the list view, it might be confusing for the user to see either the changed Version or the stale Version of the name depending on how fast the Fact is propagated to the read model that is queried by the list view.

What if when coming back to the Listview, you could query the read model with a sidenote of "but answer this query only after you have consumed the change i just made, or bring the stale version after a timeout of 500ms".

#### Publishing Transactions

In order to do this, you'd need to have a handle to express which transaction you are referring to, even more important, you'd need the read model to consume the fact identified by the handle, otherwise you'd run into the timeout every time.

To make this easy there is the concept of a MarkFact. If you use publishWithMark, a special Fact is appended to the list of Facts you publish, that – by default – every consumer receives, and its id is returned as a handle to pass to consumers.

#### Efficiency

While it might seem wasteful to send these MarkFacts to *all* consumers that did not explicitely ask FactCast not to, it actually helps consumers that get only occasional Facts, as it reduces the number of of Facts to scan in follow mode.
