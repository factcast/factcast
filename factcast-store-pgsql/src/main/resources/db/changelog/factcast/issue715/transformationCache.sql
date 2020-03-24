CREATE TABLE transformationcache(
	cache_key 		varchar(2048) PRIMARY KEY,
	
---- text is used here over jsonb in order to take load off the database that might occur when 
---- unnecessarily parsing the data.	
	
	header 			text NOT NULL,
	payload 		text NOT NULL,

----   last_access is intentionally not indexed, due to the fact, that it will be very frequently 
----   updated and cleanup can safely use a FTS, due to its nature (it is async)
	
	last_access 		TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);
