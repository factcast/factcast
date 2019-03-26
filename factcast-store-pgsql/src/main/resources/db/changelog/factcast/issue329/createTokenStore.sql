--liquibase formatted sql
--changeset usr:issue329

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE IF NOT EXISTS tokenstore (
	token 	UUID 		PRIMARY KEY 		DEFAULT uuid_generate_v4(),
	ns	 	varchar 	NOT NULL,
	state 	JSONB 		NOT NULL,
	ts	 	TIMESTAMP 						DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tokenstore_ts ON tokenstore(ts);

 
