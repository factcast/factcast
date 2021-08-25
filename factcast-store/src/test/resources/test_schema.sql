#
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DROP TRIGGER IF EXISTS tr_fact_insert ON fact;
DROP SEQUENCE IF EXISTS catchup_seq;
DROP TABLE IF EXISTS fact CASCADE;
DROP TABLE IF EXISTS catchup CASCADE;
DROP TABLE IF EXISTS schemastore cascade;
DROP TABLE IF EXISTS tokenstore cascade;
DROP TABLE IF EXISTS snapshot_cache cascade;
DROP TABLE IF EXISTS transformationstore cascade;
DROP TABLE IF EXISTS transformationcache cascade;

CREATE TABLE fact (
 ser SERIAL PRIMARY KEY,
 
 header JSONB NOT NULL,
 payload JSONB NOT NULL

);

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

#

CREATE TABLE IF NOT EXISTS tokenstore (
	token 	UUID 		PRIMARY KEY 		DEFAULT uuid_generate_v4(),
	ns	 	varchar 	,
	state 	JSONB 		NOT NULL,
	ts	 	TIMESTAMP 						DEFAULT now()
);

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

CREATE TABLE transformationstore(
    id 				varchar(2048) PRIMARY KEY,
    hash 			varchar(32),
    ns 				varchar(255) NOT NULL,
    type 			varchar(255) NOT NULL,
    from_version 	int NOT NULL,
    to_version       int NOT NULL,
    transformation 	text
);

 CREATE TABLE IF NOT EXISTS transformationcache(
	cache_key 		varchar(2048) PRIMARY KEY,
	header 			text NOT NULL,
	payload 		text NOT NULL,
	last_access 		TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));

create table IF NOT EXISTS snapshot_cache (
	cache_key 		varchar(2048) not null,

-- note that the UUID is part of the primary key, so it is not nullable.
-- while it makes sense to use a separate uuid when storing aggregate states, it is
-- completely useless with projection states. In this case we store a fake uuid(0,0) in order to maintain the PK constraint

	uuid     		uuid not null,

-- represents the state of the blob (processed all relevant facts up to factid)

	factid          uuid not null,

	data 			bytea not null,

-- indicated if the data is already compressed in order to bypass transport compression if possible

	compressed      boolean not null,

	last_access     timestamp with time zone default now() not null,
	created_at    	timestamp with time zone default now() not null,

    primary key (uuid,cache_key)
);


ALTER TABLE ONLY public.catchup
    ADD CONSTRAINT catchup_pkey PRIMARY KEY (cid, ser);

CREATE UNIQUE INDEX idx_fact_unique_uuid ON public.fact USING btree ((((header ->> 'id'::text))::uuid));
CREATE INDEX idx_fact_header ON public.fact USING gin (header jsonb_path_ops);
CREATE INDEX idx_schemastore ON public.schemastore USING btree (ns, type, version);
CREATE INDEX idx_tokenstore_ts ON public.tokenstore USING btree (ts);
CREATE INDEX idx_transformationstore ON public.transformationstore USING btree (ns, type);
CREATE INDEX index_for_enum ON public.fact USING btree (((header ->> 'ns'::text)), ((header -> 'type'::text)));
CREATE INDEX transformationcache_last_access ON public.transformationcache USING btree (last_access);
CREATE INDEX snapshot_cache_last_access ON snapshot_cache USING BTREE (last_access);


 DROP VIEW IF EXISTS stats_index;                                                                                            
 CREATE VIEW stats_index AS                                                                                                  
 SELECT t.schemaname,                                                                                                        
        t.tablename,                                                                                                         
        c.reltuples::bigint                            AS num_rows,                                                          
        pg_size_pretty(pg_relation_size(c.oid))        AS table_size,                                                        
        psai.indexrelname                              AS index_name,                                                        
        pg_size_pretty(pg_relation_size(i.indexrelid)) AS index_size,                                                        
        CASE WHEN i.indisunique THEN 'Y' ELSE 'N' END  AS "unique",                                                          
        psai.idx_scan                                  AS number_of_scans,                                                   
        psai.idx_tup_read                              AS tuples_read,                                                       
        psai.idx_tup_fetch                             AS tuples_fetched,                                                    
        CASE                                                                                                                 
            WHEN i.indisvalid THEN 'Y'                                                                                       
            ELSE                                                                                                             
                CASE                                                                                                         
                    WHEN i is null THEN '-'                                                                                  
                    ELSE                                                                                                     
                        'N' END                                                                                              
            END                                                                                                              
                                                       AS "valid"                                                            
 FROM pg_tables t                                                                                                            
          LEFT JOIN pg_class c ON t.tablename = c.relname                                                                    
          LEFT JOIN pg_index i ON c.oid = i.indrelid                                                                         
          LEFT JOIN pg_stat_all_indexes psai ON i.indexrelid = psai.indexrelid                                               
 WHERE t.schemaname NOT IN ('pg_catalog', 'information_schema')                                                              
 ORDER BY 1, 2;    