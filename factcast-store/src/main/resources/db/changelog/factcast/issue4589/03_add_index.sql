CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transformationcache_fact_id
    ON transformationcache (fact_id);
