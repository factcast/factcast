---
title: "Excluding Facts"
weight: 150
type: docs
---

> Prior to the migration to version 0.13.0 this mechanism was called `blacklisting`.

In rare occasions it can happen that one or more Facts were emitted that are broken in a way that makes it necessary to
remove them from the Fact stream altogether. Events including or referencing malware might be an example.

**Exclusion** provides a way to prevent single Facts from being delivered to
any consumers, without the need to actually delete them from the history.

{{% alert title="A word of caution" color="warning" %}}
Please remember that removing or altering Facts that already got emitted from the Fact-Stream, no matter if through
deletion or exclusion, should be avoided whenever it is possible as this contradicts the core principle in
event-sourcing that Facts are immutable. Also remember that excluding a Fact won't revert that consumers might
have processed and reacted to that Fact already. Therefor, removing via exclusion might prevent reproducing the current
state of the system.
{{% /alert %}}

If nevertheless you need to exclude Facts, you can do so as follows:

## Excluding one or multiple Facts

To exclude a Fact from being served to consumers in the future, you'll need access to the `fact` table.
Setting the `exclusion_reason` field to any value will result in it being excluded from all FactStreams.

For multiple Facts it is recommended to apply this change within one transaction to prevent setting of the update
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

## Exclusion prior to version 0.13.0

If you use FactCast on a version prior to 0.13.0, you have two ways for excluding (blacklisting) Facts:

### (Deprecated) The postgres blacklist _(default)_

Blocked FactIds can be added to a table named `blacklist` within the postgresDB. Inserting a new FactId into the table  
triggers a notification that is sent to the FactCast and updates the internal representations of the running FactCast
Servers to make sure that changes take immediate effect.

In order to document why the Facts have been blacklisted, you can use the reason column (of type text). It will
not be use for anything else, so there are no expectations on the content.

### (Deprecated) The filesystem blacklist

As an alternative you can provide a list of blocked FactIds in JSON format from a file located in the classpath or the
filesystem. Consult the [properties page](/setup/properties#blacklist) on how to set this up.

{{% alert  color="info" %}}
Keep in mind that this feature has very limited use-cases and in both implementations, the list of IDs is kept in memory for
performance reasons, so please keep it very, very short.  
{{% /alert %}}
