CREATE TABLE transformationstore(
	id 				varchar(2048) PRIMARY KEY,
	hash 			varchar(32),
	ns 				varchar(255) NOT NULL,
	type 			varchar(255) NOT NULL,
	from_version 	int NOT NULL,
    to_version       int NOT NULL,
	transformation 	text
);

CREATE INDEX idx_transformationstore on transformationstore(ns,type);
