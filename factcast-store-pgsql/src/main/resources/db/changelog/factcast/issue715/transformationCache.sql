CREATE TABLE transformationcache(
	cache_key 		varchar(2048) PRIMARY KEY,
	header 			text NOT NULL,
	payload 		text NOT NULL,
	last_access 		TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);
