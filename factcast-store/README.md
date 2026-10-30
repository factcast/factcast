# factcast-store

PostgreSQL based implementation of a FactStore.

Uses LISTEN / NOTIFY in order to query with minimum latency and relies on JSONB Columns for indexing/filtering.

The opt-in [concurrent publication benchmark](../factcast-store-benchmark/concurrent-publish/README.md)
compares `FactStore.publish` throughput between releases and generates tables and bar charts.

# Schema registries

The following protocols exist for reading schemata:

- http://...
- https://...
- classpath:...
- file:///...
