---
title: "PostgreSQL"
type: docs
weight: 800
description: PostgreSQL specific tips
---

# General Tips

## Optimize GIN indexes updates

While GIN Indexes make querying jsonb faster they are also expensive to update. Especially because a single change can
cause the update of multiple index entries. In order to keep the overhead on write and update statements low, postgres
per default enables the `fastupdate` setting which defers the update of the index and instead gathers changes to execute
them all at once. This update happens when:

- the `gin_pending_list_limit` is reached (default 4MB)
- the `gin_clean_pending_list` function is called
- at the end of the autovacuum of the table

This can cause the query whose change eventually fills the `gin_pending_list_limit` to be a lot slower than usual. If
this kind of behavior is observed it might make sense to consider to:

- reduce the `gin_pending_list_limit` -> more frequent, smaller flushes
- increase the limit and do manual flushes outside of workload
- turn off `fastupdate`
- let autovacuum run more often or manually call the clean operation

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

---

# When used as AWS RDS

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
# allows to deploy major version updates via blue/green deployments, significantly reducing downtime
rds.logical_replication:'1'
```
