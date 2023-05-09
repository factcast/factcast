---
title: "Blacklisting"
weight: 110
type: docs
---

In rare occasions it can happen that one or more facts were emitted that are broken in a way that makes it necessary to
remove them from the fact stream altogether. Blacklisting provides a way to prevent single facts from being delivered to 
any consumers, without the need to actually delete them from the history.

#### A word of caution:
Please remember that removing or altering facts that already got emitted from the fact stream, no matter if through 
deletion or blacklisting, should be avoided whenever it is possible as this contradicts the core principle in 
event-sourcing that facts are immutable. Also remember that just blacklisting a fact won't revert that consumers might 
have processed and reacted to that fact already and removing it later might prevent reproducing the current state of the 
system.

## The postgres blacklist _(default)_

Blocked facts are added to a table named "blacklist" within the postgresDB. Inserting a new factId into the table  
triggers a notification that is sent to the FactCast and updates the internal representation to make sure that changes 
take immediate effect.

## The filesystem blacklist

As an alternative you can provide a list of blocked fact-ids in JSON format from a file located in the classpath or the 
filesystem. Consult the [properties page](/setup/properties) on how to set this up.


