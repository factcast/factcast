-- Only execute this changeset once the new index was created via `create_partial_idx_fact_header_active.sql` AND the
-- server has switched over to the new exclusion mechanism by setting `useInternalExclusion` to true. Be aware that
-- rolling back after this change will require the index re-creation first.
DROP INDEX IF EXISTS idx_fact_header;