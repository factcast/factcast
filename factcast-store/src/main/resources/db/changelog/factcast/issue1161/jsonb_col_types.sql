
-- does not change much (https://www.postgresql.org/docs/8.0/sql-altertable.html)
-- "Note that SET STORAGE doesn't itself change anything in the table, it just sets the strategy to be pursued during future table updates. See Section 49.2 for more information."
ALTER TABLE fact
    ALTER COLUMN header SET STORAGE MAIN,
    ALTER COLUMN payload SET STORAGE EXTENDED;

-- probably not problematic as schemastore will contain a limited number of rows
ALTER TABLE schemastore
    ALTER COLUMN jsonschema SET STORAGE EXTENDED,
    ALTER COLUMN jsonschema TYPE jsonb USING jsonschema::jsonb;
-- flush the statement cache
DISCARD ALL;

-- depending on the size of your transformationcache, this can take a while.
-- if you want to play safe, cannot afford any downtime and reduce the
-- locks duration, you can TRUNCATE the table before upgrading, keeping in mind
-- that factcast needs the time to re-transform those facts on the fly again, of course.
ALTER TABLE transformationcache
    ALTER COLUMN header SET STORAGE MAIN,
    ALTER COLUMN header TYPE jsonb USING header::jsonb,
    ALTER COLUMN payload SET STORAGE EXTENDED,
    ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
-- flush the statement cache
DISCARD ALL;
