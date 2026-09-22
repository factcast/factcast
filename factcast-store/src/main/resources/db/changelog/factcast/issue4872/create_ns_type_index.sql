CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transformation_cache_ns_type
    ON transformation_cache ((header ->> 'ns'), (header ->> 'type'));
