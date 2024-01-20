-- noinspection SqlNoDataSourceInspectionForFile

-- looks like pg may starve if we're replacing a function used in a trigger that is busy.
-- so even if we'd risk to loose a notify, we're now dropping the trigger first

DROP TRIGGER IF EXISTS tr_deferred_schemastore_insert ON schemastore;

CREATE OR REPLACE FUNCTION notifySchemaStoreInsert() RETURNS trigger AS
$$
DECLARE
    ns       varchar;
    type     varchar;
    version  int;
BEGIN
    ns := NEW.ns;
    type := NEW.type;
    version := NEW.version;
    PERFORM pg_notify('schemastore_change', json_build_object(
            'ns', ns,
            'type', type,
            'version', version,
            'txId', txid_current()
        )::text);
    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tr_deferred_schemastore_insert
    AFTER INSERT
    ON schemastore DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION notifySchemaStoreInsert();