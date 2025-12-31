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
                SET last_access = CURRENT_DATE
            -- avoid unnecessary transactions
            WHERE excluded.cache_key = i
              AND excluded.last_access < CURRENT_DATE;
        END LOOP;

    -- not possible in functions.
    -- COMMIT;

    RETURN QUERY
        SELECT tc.header, tc.payload
        FROM transformationcache tc
        WHERE cache_key = ANY (keys);
END;
$$;


