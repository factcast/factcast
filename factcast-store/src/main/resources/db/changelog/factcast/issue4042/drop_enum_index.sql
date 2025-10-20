-- we're using published_schema_versions instead of the fact table by now, so that this index is no longer needed.
DROP INDEX IF EXISTS index_for_enumeration;
