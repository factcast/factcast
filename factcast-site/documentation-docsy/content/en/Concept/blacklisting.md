---
title: "Blacklisting"
weight: 150
type: docs
---

In rare occasions it can happen that one or more facts were emitted that are broken in a way that makes it necessary to
remove them from the fact stream altogether. Events including or referencing malware might be an example.

**Blacklisting** provides a way to prevent single facts from being delivered to
any consumers, without the need to actually delete them from the history.

{{% alert title="A word of caution" color="warning" %}}
Please remember that removing or altering facts that already got emitted from the fact stream, no matter if through
deletion or blacklisting, should be avoided whenever it is possible as this contradicts the core principle in
event-sourcing that facts are immutable. Also remember that just blacklisting a fact won't revert that consumers might
have processed and reacted to that fact already and removing it later might prevent reproducing the current state of the
system.
{{% /alert %}}

If nevertheless you need to blacklist facts, there are two options:

## The postgres blacklist _(default)_

Blocked fact IDs can be added to a table named "blacklist" within the postgresDB. Inserting a new factId into the table  
triggers a notification that is sent to the FactCast and updates the internal representations of the running Factcast
Servers to make sure that changes take immediate effect.

In order to document why the facts have been blacklisted, you can use the reason column (of type text). It will
not be use for anything else, so there are no expectations on the content.

## The filesystem blacklist

As an alternative you can provide a list of blocked fact-ids in JSON format from a file located in the classpath or the
filesystem. Consult the [properties page](/setup/properties#blacklist) on how to set this up.

{{% alert  color="info" %}}
Keep in mind that this feature has very limited use-cases and in both implementations, the list of IDs is kept in memory for
performance reasons, so please keep it very, very short.  
{{% /alert %}}
