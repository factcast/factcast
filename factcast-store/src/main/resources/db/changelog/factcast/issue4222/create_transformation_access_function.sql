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
            ON CONFLICT DO NOTHING;
        END LOOP;

    RETURN QUERY
        SELECT tc.header, tc.payload
        FROM transformationcache tc
        WHERE cache_key = ANY (keys);
END;
$$;


