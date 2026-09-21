ALTER INDEX idx_fact_header SET (
    fastupdate = true,
    -- 64 MB
    gin_pending_list_limit = 65536
);

ALTER TABLE fact SET (
    -- run after 1k inserts
    autovacuum_vacuum_insert_threshold = 1000,
    autovacuum_vacuum_insert_scale_factor = 0
);
