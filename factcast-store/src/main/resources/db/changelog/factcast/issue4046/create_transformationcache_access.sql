--

CREATE TABLE IF NOT EXISTS transformationcache_access
(
    cache_key   varchar(2048),
    last_access date
);


-- might be temp. dropped and recreated during migration
CREATE UNIQUE INDEX IF NOT EXISTS idx_transformationcache_access_key ON transformationcache_access (cache_key);
CREATE INDEX IF NOT EXISTS idx_transformationcache_access_lastaccess ON transformationcache_access USING BTREE (last_access DESC);
