create table snapshot_cache_lastaccess
(
    cache_key   varchar(2048)                          not null,

    -- note that the UUID is part of the primary key, so it is not nullable.
    -- while it makes sense to use a separate uuid when storing aggregate states, it is
    -- completely useless with projection states. In this case we store a fake uuid(0,0) in order to maintain the PK constraint
    uuid        uuid                                   not null,

    last_access timestamp with time zone default now() not null,

    primary key (uuid, cache_key)
);

-- temporary, will be dropped and recreated during migration
CREATE INDEX IF NOT EXISTS idx_snapshot_cache_lastaccess ON snapshot_cache_lastaccess USING BTREE (last_access);
