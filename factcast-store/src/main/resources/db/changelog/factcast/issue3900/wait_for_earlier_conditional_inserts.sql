-- noinspection SqlNoDataSourceInspectionForFile

CREATE OR REPLACE FUNCTION waitForEarlierConditionalInserts()
    RETURNS boolean AS
$$
BEGIN
    RETURN waitForEarlierConditionalInserts(interval '100 microsecond');
end;
$$ LANGUAGE plpgsql STRICT
                    PARALLEL SAFE;

CREATE OR REPLACE FUNCTION waitForEarlierConditionalInserts(pollInterval interval)
    RETURNS boolean AS
$$
BEGIN
    RETURN waitForEarlierConditionalInserts(pollInterval, interval '100 millisecond', interval '3 seconds');
end;
$$ LANGUAGE plpgsql STRICT
                    PARALLEL SAFE;

CREATE OR REPLACE FUNCTION waitForEarlierConditionalInserts(pollInterval interval, maxPollInterval interval, maxRuntime interval)
    RETURNS boolean AS
$$
DECLARE
    i      INTEGER;
    txid   TEXT;
    delay  INTERVAL;
    failAt TIMESTAMP := clock_timestamp() + maxRuntime;
BEGIN
    txid := txid_current()::text;
    delay := pollInterval;

    IF (maxPollInterval < pollInterval)
    THEN
        RAISE 'maxPollInterval must be >= pollInterval';
    end if;

    -- we only have to wait for conditional inserts as the others are guaranteed to be comitted already or
    -- have serials AFTER the ones used in this tx

    WHILE (clock_timestamp() < failAt)
        LOOP
            IF (select not exists(select 1
                                  from pg_stat_activity
                                  where backend_xid is not null
                                    AND application_name = 'fc_cond_ins'
                                    AND xact_start < transaction_timestamp()))
            THEN
                RETURN true;
            END IF;
            PERFORM pg_sleep_for(least(delay, maxPollInterval));
            IF (delay < maxPollInterval) THEN
                delay := least(delay * 1.1, maxPollInterval);
            END IF;
        END LOOP;
    RETURN false;
END
$$ LANGUAGE plpgsql STRICT
                    PARALLEL SAFE;

