--
-- drop the indexes
DROP INDEX IF EXISTS idx_transformationcache_access_key;
DROP INDEX IF EXISTS idx_transformationcache_access_lastaccess;

-- migrate existing timestamps
INSERT INTO transformationcache_access(cache_key, last_access)
        (SELECT cache_key, last_access::date FROM transformationcache)
ON CONFLICT DO NOTHING;
-- should not happen, but you never know

-- recreate indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_transformationcache_access_key ON transformationcache_access (cache_key);
CREATE INDEX IF NOT EXISTS idx_transformationcache_access_lastaccess ON transformationcache_access USING BTREE (last_access DESC);

-- the dropping of the deprecated column should be done manually, once rollback to a version before is no longer necessary
--
-- Also, be warned, that this might take quite a while and create quite some I/O
--
-- ALTER table transformationcache
--     drop column last_access;




