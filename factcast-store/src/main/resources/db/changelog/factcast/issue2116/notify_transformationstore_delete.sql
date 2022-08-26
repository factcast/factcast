-- noinspection SqlNoDataSourceInspectionForFile

-- looks like pg may starve if we're replacing a function used in a trigger that is busy.
-- so even if we'd risk to loose a notify, we're now dropping the trigger first

DROP TRIGGER IF EXISTS tr_deferred_transformationstore_delete ON transformationstore;
CREATE OR REPLACE FUNCTION notifyTrasformationStoreDelete() RETURNS trigger AS

$$
DECLARE
    ns           varchar;
    type         varchar;
    -- dunno if we need all this stuff
    -- we'll see
BEGIN
    ns := OLD.ns;
    type := OLD.type;
    PERFORM pg_notify('transformationstore_delete', json_build_object(
            'ns', ns,
            'type', type,
            'txId', txid_current()
        )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE CONSTRAINT TRIGGER tr_deferred_transformationstore_delete
    AFTER DELETE
    ON transformationstore DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE notifyTrasformationStoreDelete();
