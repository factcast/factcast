-- on fact insert we're saving the type and version of the fact to
-- prevent extensive queries when enumerating them later.

CREATE TABLE IF NOT EXISTS published_schema_versions
(
    ns      varchar(255),
    type    varchar(255),
    version int,
    UNIQUE (ns, type, version)
);

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
    VALUES (ns, type, version)
    ON CONFLICT DO NOTHING;
    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER save_published_schema_version
    AFTER INSERT
    ON fact
    FOR EACH ROW
EXECUTE FUNCTION notifyTypeAndVersionOnInsert();