drop table if exists notification cascade;
create unlogged table notification
(
    ser   bigserial,
    -- time window (epoch/100)
    tw    bigint  default (extract(epoch FROM now()) * 10)::bigint,
    ns    varchar,
    type  varchar,
    nudge boolean default false
);

-- for finding the last notify timestamp
DROP INDEX IF EXISTS idx_notification_max;
create index if not exists idx_notification_max on notification (tw) where nudge = true;

-- to deduplicate ns/type pairs within 100 msec
DROP INDEX IF EXISTS idx_notification_dedup;
create unique index idx_notification_dedup on notification (tw desc, ns, type);

CREATE OR REPLACE FUNCTION notificationInsert() RETURNS trigger AS
$$
DECLARE
    current bigint;
BEGIN
    current := (extract(epoch FROM now()) * 10)::bigint;
    IF NOT (EXISTS(SELECT ser from notification where tw = current and nudge IS TRUE)) THEN
        -- we accept that in high concurrency situations there might be
        -- multiple nudge notifications within 100msec, as on the server side
        -- this would just restart the timer. This might be faster than using
        -- explicit locking.
        --
        -- ok, updating after write is not efficient, but the alternative (before on row) is not either
        UPDATE notification set nudge= true where ser in (SELECT max(ser) from new_rows WHERE tw = current);
        PERFORM pg_notify('nudge', json_build_object('txId', txid_current())::text);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_notification_insert ON notification;
CREATE TRIGGER tr_notification_insert
    AFTER INSERT
    ON notification
    REFERENCING NEW TABLE AS new_rows
    FOR EACH STATEMENT
EXECUTE FUNCTION notificationInsert();

CREATE OR REPLACE PROCEDURE notificationCleanup() AS
$$
BEGIN
    -- delete all notifications older than one minute
    DELETE FROM notification WHERE tw < (extract(epoch FROM clock_timestamp()) * 10 - 600);
END;
$$ LANGUAGE plpgsql;
