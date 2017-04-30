### factcast-store-pgsql

PostgreSQL based implementation of a FactStore.  

Uses LISTEN / NOTIFY in order to query with minimum latency and relies on JSONB Columns for indexing/filtering. 
