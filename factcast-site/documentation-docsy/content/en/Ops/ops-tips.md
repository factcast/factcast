---
title: "General Operations Tips"
type: docs
weight: 1000
description: Tips to improve performances or to cover some corner use cases.
---

## Conditional execution date2serial migration

In FactCast v0.7.1, a new UI feature was introduced to allow filtering of events based on their publishing date. For
this purpose, the `date2serial` mapping table was introduced in the schema.

A Liquibase changeset takes care of creating and populating the `date2serial` table, but it is **not executed** when the
store contains more than **10 million events**. This is to prevent the migration from taking too long on larger setups.

As mentioned in the changeset comments, it is suggested to run the changeset manually in such cases. The changeset can
be found in the `factcast-store` module under [
`src/main/resources/db/changelog/factcast/issue2479/date2serial_for_existing_events.sql`](https://github.com/factcast/factcast/blob/master/factcast-store/src/main/resources/db/changelog/factcast/issue2479/date2serial_for_existing_events.sql).
