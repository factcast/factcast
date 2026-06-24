-- Drop the access-bumping function before the table it reads from.
DROP FUNCTION IF EXISTS selectTransformations(varchar[]);

-- The one-off migration procedure from issue4151 also references
-- transformationcache_access; it is no longer needed.
DROP PROCEDURE IF EXISTS migrate_transformationcache_batch(integer);

-- The FK from transformationcache_access -> transformationcache (issue4222)
-- is dropped implicitly by the DROP TABLE below.
DROP TABLE IF EXISTS transformationcache_access;

-- The HASH index on cache_key was only there to back the old
-- "DELETE ... WHERE cache_key LIKE 'factId%'" path; the new fact_id index
-- replaces it.
DROP INDEX IF EXISTS idx_transformationcache_cache_key_hash;

-- last_access on the main table is replaced by created_at.
ALTER TABLE transformationcache
    DROP COLUMN last_access;
