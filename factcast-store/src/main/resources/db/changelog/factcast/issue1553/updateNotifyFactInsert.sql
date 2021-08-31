CREATE OR REPLACE FUNCTION notifyFactInsert() RETURNS trigger AS
$$
BEGIN
    PERFORM pg_notify('fact_insert', json_build_object(
            'ser', NEW.ser,
            'header', NEW.header,
            'txId', txid_current()
        )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_deferred_fact_insert ON fact;
CREATE CONSTRAINT TRIGGER tr_deferred_fact_insert AFTER INSERT ON fact DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();
