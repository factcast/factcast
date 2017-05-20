#
DROP TRIGGER IF EXISTS tr_fact_insert ON fact;
DROP INDEX IF EXISTS idx_fact_header;
DROP INDEX IF EXISTS idx_fact_unique_id;
DROP TABLE IF EXISTS fact CASCADE;
DROP TABLE IF EXISTS catchup CASCADE;
DROP SEQUENCE IF EXISTS catchup_seq;
#

CREATE TABLE fact (
 ser SERIAL PRIMARY KEY,
 
 header JSONB NOT NULL,
 payload JSONB NOT NULL

);

CREATE UNIQUE INDEX idx_fact_unique_id ON fact( (header->'id') );
CREATE INDEX idx_fact_header ON fact USING GIN(header jsonb_path_ops);
#

CREATE OR REPLACE FUNCTION notifyFactInsert() RETURNS trigger AS $$
BEGIN
  PERFORM pg_notify('fact_insert', json_build_object(
    'ser', NEW.ser,
    'header',NEW.header
  )::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

#

CREATE TRIGGER tr_fact_insert AFTER INSERT ON fact FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();

#
create sequence catchup_seq;
create UNLOGGED table catchup (
 client bigint, 
 ser bigint, 
 ts timestamp
); 
create index on catchup(client,ser); 
#