#
DROP TRIGGER IF EXISTS tr_fact_insert ON fact;
DROP SEQUENCE IF EXISTS catchup_seq;


DROP INDEX IF EXISTS idx_catchup_cid_ser;
DROP INDEX IF EXISTS idx_fact_header;
DROP INDEX IF EXISTS idx_fact_unique_id;

DROP TABLE IF EXISTS fact CASCADE;
DROP TABLE IF EXISTS catchup CASCADE;

DROP TABLE IF EXISTS schemastore cascade;
DROP TABLE IF EXISTS transformationstore cascade;
DROP TABLE IF EXISTS transformationcache cascade;

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
CREATE CONSTRAINT TRIGGER tr_deferred_fact_insert AFTER INSERT ON fact DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();

#
create sequence catchup_seq;
create table catchup (
 cid bigint, 
 ser bigint, 
 ts timestamp
); 
create index idx_catchup_cid_ser on catchup(cid,ser); 

#
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE IF NOT EXISTS tokenstore (
	token 	UUID 		PRIMARY KEY 		DEFAULT uuid_generate_v4(),
	ns	 	varchar 	,
	state 	JSONB 		NOT NULL,
	ts	 	TIMESTAMP 						DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tokenstore_ts ON tokenstore(ts);

 CREATE TABLE IF NOT EXISTS schemastore(
	id 				varchar(2048)  PRIMARY KEY, 
	hash 			varchar(32),
	ns 				varchar(255),
	type 			varchar(255),
	version 		int,
	jsonschema 		text,
	UNIQUE(id),
	UNIQUE(ns,type,version)	
);

CREATE INDEX IF NOT EXISTS idx_schemastore on schemastore(ns,type,version);

CREATE TABLE transformationstore(
    id 				varchar(2048) PRIMARY KEY,
    hash 			varchar(32),
    ns 				varchar(255) NOT NULL,
    type 			varchar(255) NOT NULL,
    from_version 	int NOT NULL,
    to_version       int NOT NULL,
    transformation 	text
);

CREATE INDEX IF NOT EXISTS idx_transformationstore on transformationstore(ns,type);

 CREATE TABLE IF NOT EXISTS transformationcache(
	cache_key 		varchar(2048) PRIMARY KEY,
	header 			text NOT NULL,
	payload 		text NOT NULL,
	last_access 		TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));

CREATE TABLE IF NOT EXISTS snapshot_cache(
	cache_key 		varchar(2048) not null,
	uuid     		uuid,
	factid          uuid not null,
	data 			bytea not null,
	last_access     timestamp with time zone default now() not null,
	created_at    	timestamp with time zone default now() not null,
    primary key (uuid,cache_key)
);
