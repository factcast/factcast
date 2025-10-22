--

CREATE TABLE IF NOT EXISTS transformationcache_access
(
    cache_key   varchar(2048),
    last_access date
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transformationcache_access_key ON transformationcache_access (cache_key);
CREATE INDEX IF NOT EXISTS idx_transformationcache_access_lastaccess ON transformationcache_access USING BTREE (last_access DESC);

-- migrate existing timestamps
INSERT INTO transformationcache_access(cache_key, last_access)
        (SELECT cache_key, last_access::date FROM transformationcache)
ON CONFLICT DO NOTHING;
-- should not happen, but you never know

-- the dropping of the deprecated column is in a separate file as we might decide to leave it there depending on the
-- number of rows existing




