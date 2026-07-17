DROP FUNCTION IF EXISTS notifyTypeAndVersionOnInsert CASCADE;
CREATE OR REPLACE FUNCTION notifyTypeAndVersionOnInsert() RETURNS trigger AS
$$
DECLARE
    ns      varchar;
    type    varchar;
    version int;
BEGIN
    ns := NEW.header ->> 'ns';
    type := NEW.header ->> 'type';
    version := NEW.header ->> 'version';

    INSERT INTO published_schema_versions (ns, type, version)
    SELECT DISTINCT header ->> 'ns' as ns, header ->> 'type' as type, (header ->> 'version')::int as version
    FROM new_rows
    ON CONFLICT DO NOTHING;

    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER save_published_schema_version
    AFTER INSERT
    ON fact
    REFERENCING NEW TABLE new_rows
    FOR STATEMENT
EXECUTE FUNCTION notifyTypeAndVersionOnInsert();
