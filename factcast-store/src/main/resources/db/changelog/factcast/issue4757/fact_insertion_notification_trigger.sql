CREATE OR REPLACE FUNCTION createNotificationOnFactInsert() RETURNS trigger AS
$$
BEGIN
    INSERT INTO notification(ns, type)
    VALUES (NEW.header ->> 'ns', NEW.header ->> 'type')
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_fact_notification ON fact;
CREATE CONSTRAINT TRIGGER tr_fact_notification
    AFTER INSERT
    ON fact DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE createNotificationOnFactInsert();
