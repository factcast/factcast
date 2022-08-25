-- noinspection SqlNoDataSourceInspectionForFile

-- looks like pg may starve if we're replacing a function used in a trigger that is busy.
-- so even if we'd risk to loose a notify, we're now dropping the trigger first

DROP TRIGGER IF EXISTS tr_deferred_transformationstore_delete ON transformationstore;
CREATE OR REPLACE FUNCTION notifyTrasformationStoreDelete() RETURNS trigger AS

$$
DECLARE
    notified     BOOLEAN;
    id           varchar;
    hash         varchar;
    ns           varchar;
    type         varchar;
    from_version int;
    to_version   int;
    -- dunno if we need all this stuff
    -- we'll see
BEGIN

    id := OLD.id;
    hash := OLD.hash;
    ns := OLD.ns;
    type := OLD.type;
    from_version := OLD.from_version;
    to_version := OLD.to_version;

    -- do we need this check here?
    notified := NULLIF(current_setting(CONCAT('myvars.transformationstoretrigger.', ns, '.', type, '.', from_version, '.', to_version), TRUE), '');

    IF notified IS NULL THEN
        perform set_config(CONCAT('myvars.transformationstoretrigger.', ns, '.', type, '.', from_version, '.', to_version), 'TRUE', TRUE);
        PERFORM pg_notify('transformationstore_delete', json_build_object(
                'id', id,
                'hash', hash,
                'ns', ns,
                'type', type,
                'from_version', from_version,
                'to_version', to_version,
                'txId', txid_current()
            )::text);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE CONSTRAINT TRIGGER tr_deferred_transformationstore_delete
    AFTER DELETE
    ON transformationstore DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE notifyTrasformationStoreDelete();
