CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transformationcache_cache_key_hash ON transformationcache USING HASH (cache_key);
