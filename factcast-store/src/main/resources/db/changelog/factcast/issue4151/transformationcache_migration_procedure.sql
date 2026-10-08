-- redundant, but we have to make sure these exist, as the table might be used during the batch migration
CREATE UNIQUE INDEX IF NOT EXISTS idx_transformationcache_access_key ON transformationcache_access (cache_key);
CREATE INDEX IF NOT EXISTS idx_transformationcache_access_lastaccess ON transformationcache_access USING BTREE (last_access DESC);

CREATE OR REPLACE PROCEDURE migrate_transformationcache_batch(batch_size integer DEFAULT 10000)
LANGUAGE plpgsql
AS $$
DECLARE
    v_last_access timestamp := '-infinity';
    v_last_key text := '';
    v_rows int;
BEGIN
    LOOP
        INSERT INTO transformationcache_access (cache_key, last_access)
            SELECT t.cache_key, t.last_access::date
            FROM transformationcache t
            WHERE (t.last_access, t.cache_key) > (v_last_access, v_last_key)
            ORDER BY t.last_access, t.cache_key
            LIMIT batch_size
        ON CONFLICT (cache_key) DO NOTHING;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        RAISE NOTICE 'Inserted % rows', v_rows;

        EXIT WHEN v_rows = 0;

        SELECT t.last_access, t.cache_key
        INTO v_last_access, v_last_key
        FROM transformationcache t
        WHERE (t.last_access, t.cache_key) > (v_last_access, v_last_key)
        ORDER BY t.last_access, t.cache_key
        OFFSET batch_size - 1
        LIMIT 1;

        COMMIT;

        -- throttling
        PERFORM pg_sleep(0.1);
    END LOOP;
END;
$$;
