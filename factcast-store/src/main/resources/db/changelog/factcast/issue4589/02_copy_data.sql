-- Copy existing cache entries into the new table.
-- cache_key is "<factId>-<version>-<transformationChainId>": chars 1-36 are the
-- fact id (uuid), the segment right after is the target version.
-- Guarded by a precondition in the changelog; if skipped, the new table simply
-- starts empty and repopulates on demand.
INSERT INTO transformationcache_v2 (fact_id, version, header, payload, created_at)
SELECT substring(cache_key, 1, 36)::uuid,
       split_part(substring(cache_key FROM 38), '-', 1)::int,
       header,
       payload,
       COALESCE(last_access, now())
FROM transformationcache
ON CONFLICT (fact_id, version) DO NOTHING;
