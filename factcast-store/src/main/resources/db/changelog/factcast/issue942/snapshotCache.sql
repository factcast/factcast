create table snapshot_cache (
	cache_key 		varchar(2048) not null,

-- note that the UUID is part of the primary key, so it is not nullable.
-- while it makes sense to use a separate uuid when storing aggregate states, it is
-- completely useless with projection states. In this case we store a fake uuid(0,0) in order to maintain the PK constraint

	uuid     		uuid not null,

-- represents the state of the blob (processed all relevant facts up to factid)

	factid          uuid not null,

	data 			bytea not null,

-- indicated if the data is already compressed in order to bypass transport compression if possible

	compressed      boolean not null,

	last_access     timestamp with time zone default now() not null,
	created_at    	timestamp with time zone default now() not null,

    primary key (uuid,cache_key)
);

