CREATE OR REPLACE FUNCTION createNotificationOnFactInsert()
    RETURNS trigger AS
$$
BEGIN
    INSERT INTO notification(ns, type)
    SELECT DISTINCT ON (ns,type) n.header ->> 'ns' as ns, n.header ->> 'type' as type
    FROM new_rows n
    ON CONFLICT (tw, ns, type)
        DO UPDATE set ser=nextval('notification_ser_seq');

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_fact_notification ON fact;
CREATE TRIGGER tr_fact_notification
    AFTER INSERT
    ON fact
    REFERENCING NEW TABLE AS new_rows
    FOR EACH STATEMENT
EXECUTE FUNCTION createNotificationOnFactInsert();

