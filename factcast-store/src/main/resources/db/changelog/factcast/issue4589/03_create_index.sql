-- The PRIMARY KEY already provides the btree on (fact_id, version, path), which
-- also backs "DELETE ... WHERE fact_id = ?" via its leftmost prefix.
-- Only the creation-date index is added here, built CONCURRENTLY so it does not
-- block writes to the (now live) new table.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transformation_cache_created_at
    ON transformation_cache (created_at);
