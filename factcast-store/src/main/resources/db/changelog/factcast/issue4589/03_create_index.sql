-- The PRIMARY KEY already provides the btree on (fact_id, version), which also
-- backs "DELETE ... WHERE fact_id = ?" via its leftmost prefix.
-- Only the creation-date index is added here, built CONCURRENTLY so it does not
-- block writes to the (now live) new table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transformationcache_v2_created_at
    ON transformationcache_v2 (created_at);
