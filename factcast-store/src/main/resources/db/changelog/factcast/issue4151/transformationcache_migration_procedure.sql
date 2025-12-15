-- redundant, but we have to make sure these exist, as the table might be used during the batch migration
CREATE UNIQUE INDEX IF NOT EXISTS idx_transformationcache_access_key ON transformationcache_access (cache_key);
CREATE INDEX IF NOT EXISTS idx_transformationcache_access_lastaccess ON transformationcache_access USING BTREE (last_access DESC);

CREATE OR REPLACE PROCEDURE migrate_transformationcache_batch(batch_size INTEGER)

LANGUAGE plpgsql
AS $$
DECLARE
    v_rows INTEGER;
BEGIN
    LOOP
        INSERT INTO transformationcache_access(cache_key, last_access)
        SELECT transformationcache.cache_key, transformationcache.last_access::date FROM transformationcache
        WHERE NOT EXISTS (
            -- this relies on existing cache_key index on transformationcache_access
            SELECT 1 FROM transformationcache_access WHERE transformationcache_access.cache_key = transformationcache.cache_key
        )
        LIMIT batch_size
        ON CONFLICT DO NOTHING;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        EXIT WHEN v_rows = 0;

        -- give db some room to breathe
        PERFORM pg_sleep(0.01);
    END LOOP;
END;
$$;
