-- we're renaming the function as notify* is not a good name for something that
-- does not notify at all
--
DROP FUNCTION IF EXISTS notifyTypeAndVersionOnInsert CASCADE;
DROP FUNCTION IF EXISTS memorizeTypeAndVersionOnInsert CASCADE;
CREATE OR REPLACE FUNCTION memorizeTypeAndVersionOnInsert() RETURNS trigger AS
$$
DECLARE
BEGIN
    INSERT INTO published_schema_versions (ns, type, version)
    SELECT DISTINCT header ->> 'ns' as ns, header ->> 'type' as type, (header ->> 'version')::int as version
    FROM new_rows
    ON CONFLICT DO NOTHING;

    RETURN NULL; -- result is ignored since this is an AFTER trigger
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER tr_fact_save_published_schema_version
    AFTER INSERT
    ON fact
    REFERENCING NEW TABLE new_rows
    FOR STATEMENT
EXECUTE FUNCTION memorizeTypeAndVersionOnInsert();
