-- looks like pg may starve if we're replacing a function used in a trigger that is busy.
-- so even if we'd risk to loose a notify, we're now dropping the trigger first

DROP TRIGGER IF EXISTS tr_deferred_fact_insert ON fact;
CREATE OR REPLACE FUNCTION notifyFactInsert() RETURNS trigger AS

$$
DECLARE
    notified BOOLEAN;
    ns varchar;
    type varchar;
BEGIN

    ns := NEW.header ->> 'ns';
    type := NEW.header ->> 'type';

    notified := NULLIF(current_setting(CONCAT('myvars.facttrigger.',ns,'.',type), TRUE), '');

    IF notified IS NULL THEN
        perform set_config(CONCAT('myvars.facttrigger.',ns,'.',type),'TRUE',TRUE);
        PERFORM pg_notify('fact_insert', json_build_object(
                'ser', NEW.ser,
                'header', NEW.header,
                'txId', txid_current(),
                'ns',ns,
                'type',type
            )::text);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE CONSTRAINT TRIGGER tr_deferred_fact_insert AFTER INSERT ON fact DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();
