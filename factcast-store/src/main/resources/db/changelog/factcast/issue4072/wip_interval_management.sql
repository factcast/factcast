drop table if exists notification cascade;
drop table if exists interval_notify cascade;
drop table if exists interval_cleanup cascade;

-- the table that keeps the actual notifications to be queried by the factstore.
-- It contains ns,type so that the factstore can notify its subscribed listeners.

-- the factstore should use
--
-- "with n as (select pg_xact_commit_timestamp(xmin) ct from notification order by time desc limit 250)
--   select max(ct) from n;"
--
-- to get the latest visible commit and can the run
--
-- "select distinct(ns,type) from notification
--      where pg_xact_commit_timestamp(xmin) > :lastKnown
--      AND
--      pg_xact_commit_timestamp(xmin) <= :aboveResult"
--
-- The reason we can do that is that we don't need to respect the order of notifications here, just have to be careful
-- not to miss any.
--
-- the 250 above is an optimization in order to get the commit time from only the 250 newest (in terms of timestamp
-- contained) an take the max commit time from one of those. The idea is that the time and commit time
-- should somewhat roughly correlate.
-- Not sure, if this is a good idea, or 250 is a good number of rows to look at.
create table notification
(
    ns   varchar(255),
    type varchar(255),
    time timetz default now()
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

DROP TRIGGER IF EXISTS tr_interval_cleanup ON interval_cleanup;

CREATE OR REPLACE FUNCTION maintain_intervals() RETURNS trigger AS
$$
BEGIN
    delete FROM interval_cleanup WHERE minute < current_time(0) - interval '2 minutes' OR minute > current_time(0);
    delete FROM interval_notify WHERE slot < current_time(0) - interval '2 minutes' OR slot > current_time(0);
    delete
    FROM notification
    WHERE time < current_time(0) - interval '5 minutes'
       OR (time > current_time(0) AND time < '23:55:00+02'::timetz);
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
        PERFORM pg_notify('conditional_notify', json_build_object(
                'txId', txid_current())::text);
    END IF;
    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_notification
    AFTER INSERT
    ON notification
    FOR EACH ROW
EXECUTE FUNCTION conditional_notify();
