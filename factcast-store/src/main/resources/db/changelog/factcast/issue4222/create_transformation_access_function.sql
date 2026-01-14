CREATE OR REPLACE FUNCTION selectTransformations(keys_to_query varchar[])
    RETURNS TABLE
            (
                header  jsonb,
                payload jsonb
            )
    LANGUAGE plpgsql
AS
$$
DECLARE
BEGIN
    UPDATE transformationcache_access
    SET last_access = CURRENT_DATE
    -- avoid unnecessary transactions
    WHERE cache_key = ANY (keys_to_query)
      AND transformationcache_access.last_access < CURRENT_DATE;

    -- not possible in functions.
    -- COMMIT;

    RETURN QUERY
        SELECT tc.header AS header, tc.payload AS payload
        FROM transformationcache tc
        WHERE cache_key = ANY (keys_to_query);
END;
$$;


