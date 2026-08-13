-- Copy existing cache entries into the new table.
-- cache_key is "<factId>-<version>-<path>": chars 1-36 are the fact id (uuid),
-- the next dash-delimited segment is the target version, and the remainder is
-- the transformation chain path rendered as "[1, 2, 3]".
-- The path is parsed back into an int[]: strip the "<version>-" prefix and the
-- surrounding brackets, then split on ", ".
-- Guarded by a precondition in the changelog; if skipped, the new table simply
-- starts empty and repopulates on demand.
INSERT INTO transformation_cache (fact_id, version, path, header, payload, created_at)
SELECT substring(cache_key, 1, 36)::uuid,
       split_part(substring(cache_key FROM 38), '-', 1)::int,
       string_to_array(
           trim(both '[]' from regexp_replace(substring(cache_key FROM 38), '^\d+-', '')),
           ', ')::int[],
       header,
       payload,
       COALESCE(last_access, now())
FROM transformationcache
ON CONFLICT (fact_id, version, path) DO NOTHING;
