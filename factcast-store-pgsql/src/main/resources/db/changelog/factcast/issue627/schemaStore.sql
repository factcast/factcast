CREATE TABLE schemastore(
	id 				varchar(2048)  PRIMARY KEY, 
	hash 			varchar(32),
	ns 				varchar(255),
	type 			varchar(255),
	version 		int,
	jsonschema 		text,
	UNIQUE(id),
	UNIQUE(ns,type,version)	
);

CREATE INDEX idx_schemastore on schemastore(ns,type,version);
