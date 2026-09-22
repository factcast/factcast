CREATE TYPE transformation_cache_entry_key AS
(
    fact_id uuid,
    version int,
    path    int[]
);

CREATE OR REPLACE PROCEDURE invalidate_transformation_cache(
    p_ns text, p_type text, p_from_version int, p_to_version int
)
    LANGUAGE plpgsql AS
$$
DECLARE
    keys transformation_cache_entry_key[];
BEGIN
    -- Collect only keys, without fetching potentially large headers or payloads.
    -- ACCESS SHARE allows other cache writers to finish while we scan.
    SELECT array_agg(ROW (c.fact_id, c.version, c.path)::transformation_cache_entry_key)
    INTO keys
    FROM transformation_cache c
    WHERE c.header ->> 'ns' = p_ns
      AND c.header ->> 'type' = p_type
      AND (
        -- any of the version is contained in the path
        (c.path && ARRAY [p_from_version, p_to_version])
            -- or not all versions are provided
            OR p_from_version IS NULL OR p_to_version IS NULL);

    IF keys IS NULL THEN
        RETURN;
    END IF;

    -- Use the same lock as flushing to avoid circular row locks (#3279).
    -- Rows arriving after the scan are handled by the delayed second invalidation.
    LOCK TABLE transformation_cache IN EXCLUSIVE MODE;
    DELETE
    FROM transformation_cache c
        USING unnest(keys) AS k
    WHERE c.fact_id = k.fact_id
      AND c.version = k.version
      AND c.path = k.path;
END;
$$;
