create unlogged table notification
(
    ser   bigserial,
    -- time window (epoch/100)
    tw    bigint  default (extract(epoch FROM clock_timestamp()) * 10)::bigint,
    ns    varchar,
    type  varchar,
    nudge boolean default false
);

-- for finding the last notify timestamp
create index on notification (tw desc) where nudge = true;

-- to deduplicate ns/type pairs within 100 msec
create unique index on notification (tw, ns, type);

CREATE OR REPLACE FUNCTION notificationInsert() RETURNS trigger AS
$$
BEGIN
    IF NOT (EXISTS(SELECT ser from notification where tw = NEW.tw and nudge IS TRUE)) THEN
        -- we accept that in high concurrency situations there might be
        -- multiple nudge notifications within 100msec, as on the server side
        -- this would just restart the timer. This might be faster than using
        -- explicit locking.
        NEW.nudge := TRUE;
        PERFORM pg_notify('nudge', json_build_object('txId', txid_current())::text);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_notification_insert ON notification;
CREATE TRIGGER tr_notification_insert
    BEFORE INSERT
    ON notification
    FOR EACH ROW
EXECUTE PROCEDURE notificationInsert();

CREATE OR REPLACE PROCEDURE notificationCleanup() AS
$$
BEGIN
    -- delete all notifications older than one minute
    DELETE FROM notification WHERE tw < (extract(epoch FROM clock_timestamp()) * 10 - 600);
END;
$$ LANGUAGE plpgsql;
