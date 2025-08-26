-- we do no longer reset identites here, as it would require additional
-- effort in the tests (HighwaterMark)
--
-- wherever you NEED predictable serials, please reset them yourself in your test
--
truncate table fact;
truncate table blacklist;
truncate table schemastore;
truncate table transformationstore;
truncate table transformationcache;
truncate table tokenstore;
truncate table date2serial;
truncate table published_schema_versions;

select dropAllTailIndexes();










