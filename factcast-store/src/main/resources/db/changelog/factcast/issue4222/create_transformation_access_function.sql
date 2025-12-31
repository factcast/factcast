CREATE OR REPLACE FUNCTION selectTransformations(keys varchar[])
    RETURNS TABLE
            (
                header  jsonb,
                payload jsonb
            )
    LANGUAGE plpgsql
AS
$$
DECLARE
    i varchar;
BEGIN
    FOREACH i in ARRAY keys
        LOOP
            INSERT INTO transformationcache_access
            VALUES (i, CURRENT_DATE)
            ON CONFLICT (cache_key) DO UPDATE
                SET last_access = excluded.last_access
            -- avoid unnecessary transactions
            WHERE transformationcache_access.cache_key = i
              AND transformationcache_access.last_access < excluded.last_access;
        END LOOP;

    -- not possible in functions.
    -- COMMIT;

    RETURN QUERY
        SELECT tc.header, tc.payload
        FROM transformationcache tc
        WHERE cache_key = ANY (keys);
END;
$$;


