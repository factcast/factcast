-- looks like pg may starve if we're replacing a function used in a trigger that is busy.
-- so even if we'd risk to loose a notify, we're now dropping the trigger first

DROP TRIGGER IF EXISTS tr_deferred_fact_insert ON fact;
CREATE OR REPLACE FUNCTION notifyFactInsert() RETURNS trigger AS

$$
DECLARE
    millis bigint ;
    first  bigint;
    count  int;
    abool  text;
BEGIN
    millis := (extract(epoch FROM clock_timestamp()) * 1000)::bigint;
    insert into pending_notifications
    values (millis, NEW.header ->> 'ns', NEW.header ->> 'type')
    on conflict do nothing;
    --     PERFORM pg_notify('fact_insert', CONCAT(NEW.ser, ':', NEW.header ->> 'ns', ':', NEW.header ->> 'type'));


    SELECT min(ms)
    from pending_notifications
    into first;

    if (first < millis - 100) then
        -- but only if we're not already in the middle of a flush
        WITH attempt AS (SELECT pg_try_advisory_lock(1, 8192) AS got)
        SELECT CASE WHEN got THEN flushNotifications() END
        FROM attempt
        into abool;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION flushNotifications() RETURNS void AS
$$
DECLARE
    payload text;
    hwm     bigint ;
BEGIN
    select max(ms) from pending_notifications into hwm;
    SELECT jsonb_agg(
                   jsonb_build_object(
                           'ns', ns,
                           'type', type
                   )
           ) AS rows_json
    FROM pending_notifications
    into payload;

    IF (length(payload) < 7500) THEN
        PERFORM pg_notify('fact_insert', concat(payload, ' --- ', now()::text));
    ELSE
        PERFORM pg_notify('fact_insert', '--- too many facts to send ---');--todo
    END IF;

    delete from pending_notifications where ms <= hwm;

    PERFORM pg_advisory_unlock(1, 8192);
END
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER tr_deferred_fact_insert
    AFTER INSERT
    ON fact DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE notifyFactInsert();
