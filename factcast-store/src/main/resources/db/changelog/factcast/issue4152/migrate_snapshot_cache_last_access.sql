-- drop the temp index
DROP INDEX IF EXISTS idx_snapshot_cache_lastaccess;

-- migrate existing timestamps
INSERT INTO snapshot_cache_lastaccess(cache_key, uuid, last_access)
        (SELECT cache_key, uuid, last_access::date FROM snapshot_cache)
ON CONFLICT DO NOTHING;
-- should not happen, but you never know

-- recreate index
CREATE INDEX IF NOT EXISTS idx_snapshot_cache_lastaccess ON snapshot_cache_lastaccess USING BTREE (last_access DESC);

-- dropping of the deprecated column should be done manually, once rollback to a version before is no longer necessary:
--
-- ALTER TABLE snapshot_cache
--     DROP COLUMN last_access;
