CREATE INDEX CONCURRENTLY IF NOT EXISTS transformationcache_last_access ON transformationcache USING BTREE (last_access);
