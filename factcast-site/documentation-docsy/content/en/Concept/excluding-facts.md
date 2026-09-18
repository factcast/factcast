---
title: "Excluding Facts"
weight: 150
type: docs
---

> Prior to the migration to version X.X this mechanism was called `blacklisting`.

In rare occasions it can happen that one or more facts were emitted that are broken in a way that makes it necessary to
remove them from the fact stream altogether. Events including or referencing malware might be an example.

**Exclusion** provides a way to prevent single facts from being delivered to
any consumers, without the need to actually delete them from the history.

{{% alert title="A word of caution" color="warning" %}}
Please remember that removing or altering facts that already got emitted from the fact stream, no matter if through
deletion or exclusion, should be avoided whenever it is possible as this contradicts the core principle in
event-sourcing that facts are immutable. Also remember that excluding a fact won't revert that consumers might
have processed and reacted to that fact already. Therefor, removing via exclusion might prevent reproducing the current
state of the system.
{{% /alert %}}

If nevertheless you need to exclude facts, you can do so as follows:

## Excluding one or multiple facts

To exclude a fact from being served to consumers in the future, you'll need access to the `fact` table.
Setting the `exclusion_reason` field to any value will result in it being excluded from all FactStreams.

For multiple facts it is recommended to apply this change within one transaction to prevent setting of the update
trigger multiple times.

```sql
begin;
UPDATE fact f SET exclusion_reason = 'issue-42'
    WHERE (f.header ->> 'id') IN (
        '924e21d0-f8f3-4162-9d18-8efd7656c494',
        'd0ca1057-c20a-4c32-b4a3-a00523fa471e'
    );
commit;
```

## Exclusion prior to version X.X

If you use FactCast on a version prior to X.X, you have two ways for excluding (blacklisting) facts:

### The postgres blacklist _(default)_

Blocked fact IDs can be added to a table named "blacklist" within the postgresDB. Inserting a new factId into the table  
triggers a notification that is sent to the FactCast and updates the internal representations of the running Factcast
Servers to make sure that changes take immediate effect.

In order to document why the facts have been blacklisted, you can use the reason column (of type text). It will
not be use for anything else, so there are no expectations on the content.

### The filesystem blacklist

As an alternative you can provide a list of blocked fact-ids in JSON format from a file located in the classpath or the
filesystem. Consult the [properties page](/setup/properties#blacklist) on how to set this up.

{{% alert  color="info" %}}
Keep in mind that this feature has very limited use-cases and in both implementations, the list of IDs is kept in memory for
performance reasons, so please keep it very, very short.  
{{% /alert %}}
