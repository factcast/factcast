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
