CREATE INDEX CONCURRENTLY IF NOT EXISTS snapshot_cache_last_access ON snapshot_cache USING BTREE (last_access);
