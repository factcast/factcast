-- noinspection SqlNoDataSourceInspectionForFile

-- looks like pg may starve if we're replacing a function used in a trigger that is busy.
-- so even if we'd risk to loose a notify, we're now dropping the trigger first

DROP TRIGGER IF EXISTS tr_deferred_schemastore_insert ON schemastore;
CREATE OR REPLACE FUNCTION notifySchemaStoreInsert() RETURNS trigger AS

$$
DECLARE
    notified BOOLEAN;
    id       varchar;
    hash     varchar;
    ns       varchar;
    type     varchar;
    version  int;
    -- dunno if we need all this stuff
    -- we'll see
BEGIN

    id := NEW.id;
    hash := NEW.hash;
    ns := NEW.ns;
    type := NEW.type;
    version := NEW.version;

    -- do we need this check here?
    notified := NULLIF(current_setting(CONCAT('myvars.schemastoretrigger.', ns, '.', type, '.', version), TRUE), '');

    IF notified IS NULL THEN
        perform set_config(CONCAT('myvars.schemastoretrigger.', ns, '.', type, '.', version), 'TRUE', TRUE);
        PERFORM pg_notify('schemastore_change', json_build_object(
                'id', id,
                'hash', hash,
                'ns', ns,
                'type', type,
                'version', version,
                'txId', txid_current()
            )::text);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE CONSTRAINT TRIGGER tr_deferred_schemastore_insert
    AFTER INSERT
    ON schemastore DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE notifySchemaStoreInsert();
