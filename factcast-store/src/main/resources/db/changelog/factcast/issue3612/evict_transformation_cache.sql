-- noinspection SqlNoDataSourceInspectionForFile

-- On the rare occasion that an existing fact is changed we want to clean all cached transformations of this fact,
-- to prevent that old data is returned to restarted projections.

DROP TRIGGER IF EXISTS tr_evict_transformation_cache_on_update ON fact;

CREATE OR REPLACE FUNCTION notifyExistingFactUpdated() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('fact_update', json_build_object(
            'ser', OLD.ser,
            'id', OLD.header ->> 'id'
        )::text);
RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_evict_transformation_cache_on_update
    AFTER UPDATE ON fact
    FOR EACH ROW
    EXECUTE FUNCTION notifyExistingFactUpdated();
