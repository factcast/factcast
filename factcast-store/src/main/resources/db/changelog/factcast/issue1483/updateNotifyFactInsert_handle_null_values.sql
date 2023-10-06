-- looks like pg may starve if we're replacing a function used in a trigger that is busy.
-- so even if we'd risk to loose a notify, we're now dropping the trigger first

DROP TRIGGER IF EXISTS tr_deferred_fact_insert ON fact;
CREATE OR REPLACE FUNCTION notifyFactInsert() RETURNS trigger AS

$$
DECLARE
    notified   BOOLEAN;
    ns         varchar;
    type       varchar;
    identifier varchar;
BEGIN

    ns := NEW.header ->> 'ns';
    type := NEW.header ->> 'type';
    -- postgres 14+ only supports a subset of chars for custom variable names, see https://github.com/postgres/postgres/blob/f9f31aa91f82df863a35354893978e1937863d7c/src/backend/utils/misc/guc.c#L1069
    -- so we're using md5 to hash the namespace and type, its reasonable fast
    identifier := CONCAT('myvars.facttrigger.', CONCAT('fc', MD5(CONCAT(ns, type))));

    notified := NULLIF(current_setting(identifier, TRUE), '');

    IF notified IS NULL THEN
        perform set_config(identifier, 'TRUE', TRUE);
        PERFORM pg_notify('fact_insert', json_build_object(
                'ser', NEW.ser,
                'header', NEW.header,
                'txId', txid_current(),
                'ns', ns,
                'type', type
            )::text);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE CONSTRAINT TRIGGER tr_deferred_fact_insert
    AFTER INSERT
    ON fact DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE notifyFactInsert();
