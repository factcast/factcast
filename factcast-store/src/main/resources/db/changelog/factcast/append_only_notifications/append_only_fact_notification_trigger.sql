CREATE OR REPLACE FUNCTION createNotificationOnFactInsert()
RETURNS trigger AS $$
BEGIN
    INSERT INTO notification(ns, type)
    SELECT n.header ->> 'ns', n.header ->> 'type'
    FROM new_rows n;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
