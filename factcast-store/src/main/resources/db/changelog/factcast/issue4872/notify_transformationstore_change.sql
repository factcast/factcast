DROP TRIGGER IF EXISTS tr_deferred_transformationstore_change ON transformationstore;

CREATE OR REPLACE FUNCTION notifyTransformationStoreChange() RETURNS trigger AS
$$
BEGIN
    PERFORM pg_notify('transformationstore_change', json_build_object(
            'ns', OLD.ns,
            'type', OLD.type,
            'fromVersion', OLD.from_version,
            'toVersion', OLD.to_version,
            'txId', txid_current()
                                                    )::text);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tr_deferred_transformationstore_change
    AFTER DELETE OR UPDATE
    ON transformationstore DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION notifyTransformationStoreChange();
