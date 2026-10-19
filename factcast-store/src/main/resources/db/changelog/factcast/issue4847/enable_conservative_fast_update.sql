-- Batch GIN updates with a 64 MiB pending list to reduce insert I/O.
ALTER INDEX idx_fact_header SET (
    fastupdate = on,
    gin_pending_list_limit = 65536
);

-- Remove the aggressive insert-driven VACUUM overrides
ALTER TABLE fact RESET (
    autovacuum_vacuum_insert_threshold,
    autovacuum_vacuum_insert_scale_factor
    );

-- Trigger automatic ANALYZE after ~1,000 changed rows
ALTER TABLE fact SET (
    autovacuum_analyze_threshold = 1000,
    autovacuum_analyze_scale_factor = 0
    );
