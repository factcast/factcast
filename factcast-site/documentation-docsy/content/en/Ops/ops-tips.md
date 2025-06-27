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
be found in the `factcast-store` module under [`src/main/resources/db/changelog/factcast/issue2479/date2serial_for_existing_events.sql`](https://github.com/factcast/factcast/blob/main/factcast-store/src/main/resources/db/changelog/factcast/issue2479/date2serial_for_existing_events.sql).

## Optimize GIN indexes updates

While GIN Indexes make querying jsonb faster, they are also expensive to update. Especially because a single change can
cause the update of multiple index entries. To keep the overhead on write and update statements low, postgres per
default enables the `fastupdate` setting which defers the update of the index and instead gathers changes to execute
them all at once. This update happens:

- when the `gin_pending_list_limit` is reached (default 4MB)
- when the `gin_clean_pending_list` function is called
- at the end of an autovacuum operation on a table

However, there can be certain disadvantages of `fastupdate` on GIN indexes

- query performance can suffer significantly when looking through both, the main index and pending list
- when reaching the size limits, in-query cleanups can block other queries

This can cause queries to be a lot slower than usual which we have observed in production setups. In general, if this
kind of behavior is observed, it might make sense to consider to:

- reduce the `gin_pending_list_limit` -> more frequent, smaller flushes
- increase the limit and do manual flushes outside of workload
- turn off `fastupdate`
- let autovacuum run more often or manually call the clean operation

For now, we have decided to disable the `fastupdate` setting via [`src/main/resources/db/changelog/factcast/issue3755/disable_fast_update.sql`](https://github.com/factcast/factcast/blob/main/factcast-store/src/main/resources/db/changelog/factcast/issue3755/disable_fast_update.sql)

Please note that flushing the pending list as part of disabling the `fastupdate` setting could in theory block any other
query. This is why this change set is **not executed automatically** if the attached condition senses a larger setup
(> 10 million events). In this case please execute the change set manually to disable the `fastupdate` setting.

## Autoanalyse & Autovacuum settings

Postgres has a built-in mechanism to keep the statistics up to date and to clean up dead tuples, called
[Automatic Vacuuming](https://www.postgresql.org/docs/15/runtime-config-autovacuum.html).

In most cases, it might be necessary to adjust the default `autovacuum` settings to better fit the workload and ensure
a more efficient execution of the process:

```properties
# disable autovacuum schedule based on scale factor
autovacuum_vacuum_scale_factor:0
autovacuum_analyze_scale_factor:0
# set thresholds based on approximate number of facts inserted
autovacuum_vacuum_threshold:<number of new facts each month>
autovacuum_analyze_threshold:<number of new facts each week>
```

## AWS RDS Configuration

Most of the time, the default RDS configuration of PostgreSQL is sufficient. However, in some cases, it might be
necessary to adjust some settings in the RDS Parameter Groups to improve performance.
The following settings are recommended for FactCast instances running on production stages:

```properties
# hands over concurrency considerations to kernel
effective_io_concurrency:0
# tune accordingly, consider roughly 100mb running on a db.r5.2xlarge RDS instance
work_mem:100000
# the followings might vary, depending on your non-functional requirements
log_statement:'none'
log_min_duration_statement:500
default_statistics_target:100
```
