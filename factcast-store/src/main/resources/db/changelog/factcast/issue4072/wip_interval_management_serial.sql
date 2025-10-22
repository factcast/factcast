drop table if exists notification cascade;
drop table if exists interval_notify cascade;
drop table if exists interval_cleanup cascade;

-- the table that keeps the actual notifications to be queried by the factstore.
-- It contains ns,type so that the factstore can notify its subscribed listeners.

-- for now, we assume that all writes to the notification table happen in a serialized manner
-- (no concurrent inserts, currently implemented by advlock).

-- the factstore should use
-- "select ns,type,max(ser) ser from notification where ser > :lastKnown group by ns,type order by ser;"
create table notification
(
    ser  serial,
    ns   varchar(255),
    type varchar(255),
    time timestamp default now()
);

-- allows insertion every 100msec
create table interval_notify
(
    slot timetz default current_time(1) -- 100msec resolution
);

-- allows insertion every minute
create table interval_cleanup
(
    minute timetz default date_trunc('minute', now())
);

create unique index unique_interval_cleanup on interval_cleanup (minute);
create unique index unique_interval_notify on interval_notify (slot);
create index idx_notification_time on notification (time);
create unique index idx_notification_ser on notification (ser desc);

DROP TRIGGER IF EXISTS tr_interval_cleanup ON interval_cleanup;

CREATE OR REPLACE FUNCTION maintain_intervals() RETURNS trigger AS
$$
BEGIN
    delete from interval_cleanup where minute not in (select minute from interval_cleanup order by minute desc limit 3);
    delete from interval_notify where slot not in (select slot from interval_notify order by slot desc limit 3);
    delete
    FROM notification
    WHERE time < now() - interval '2 minutes';
    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_interval_cleanup
    AFTER INSERT
    ON interval_cleanup
    FOR EACH ROW
EXECUTE FUNCTION maintain_intervals();

DROP TRIGGER IF EXISTS tr_interval_notify ON interval_notify;

CREATE OR REPLACE FUNCTION cleanup_interval_tables() RETURNS trigger AS
$$
BEGIN
    insert into interval_cleanup values (default) on conflict do nothing;
    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_interval_notify
    AFTER INSERT
    ON interval_notify
    FOR EACH ROW
EXECUTE FUNCTION cleanup_interval_tables();


DROP TRIGGER IF EXISTS tr_notification ON notification;

CREATE OR REPLACE FUNCTION conditional_notify() RETURNS trigger AS
$$
DECLARE
    row_count INTEGER;
BEGIN
    insert into interval_notify values (default) on conflict do nothing;
    GET DIAGNOSTICS row_count = ROW_COUNT;
    IF row_count > 0 THEN
        PERFORM pg_notify('interval_notify', json_build_object('txId', txid_current())::text);
    END IF;
    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_notification
    AFTER INSERT
    ON notification
    FOR EACH ROW
EXECUTE FUNCTION conditional_notify();
