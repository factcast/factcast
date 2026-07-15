CREATE OR REPLACE FUNCTION createNotificationOnFactInsert() RETURNS trigger AS
$$
BEGIN
    INSERT INTO notification(ns, type)
    VALUES (NEW.header ->> 'ns', NEW.header ->> 'type')
    ON CONFLICT (tw, ns, type)
        DO UPDATE set ser=nextval('notification_ser_seq');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_fact_notification ON fact;
CREATE CONSTRAINT TRIGGER tr_fact_notification
    AFTER INSERT
    ON fact DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE createNotificationOnFactInsert();
