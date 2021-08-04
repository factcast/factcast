# factcast-store

PostgreSQL based implementation of a FactStore.

Uses LISTEN / NOTIFY in order to query with minimum latency and relies on JSONB Columns for indexing/filtering.

# Schema registries

The following protocols exist for reading schemata:

- http://...
- https://...
- classpath:...
- file:///...
