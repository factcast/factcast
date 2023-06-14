-- on BEFORE insert, add serial as well as timestamp to header.meta

DROP TRIGGER IF EXISTS tr_fact_augment ON fact CASCADE;
DROP FUNCTION IF EXISTS augmentSerialAndTimestamp CASCADE;

CREATE FUNCTION augmentSerialAndTimestamp() RETURNS trigger AS
$$
BEGIN
    SELECT jsonb_set(
                   NEW.header,
                   '{meta}',
                   COALESCE(NEW.header -> 'meta', '{}') ||
                   CONCAT('{',
                          '"_ser":', NEW.ser, ',',
                          '"_ts":',
                          TRUNC(EXTRACT(EPOCH FROM now()::timestamptz(3)) * 1000), -- newer postgres versions return decimals after multiplication here, we have to truncate them
                          '}')::jsonb,
                   true)
    INTO NEW.header;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_fact_augment
    BEFORE INSERT
    ON fact
    FOR EACH ROW
EXECUTE PROCEDURE augmentSerialAndTimestamp();

