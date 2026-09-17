truncate table fact restart identity;
truncate table blacklist restart identity;
truncate table schemastore restart identity;
truncate table transformationstore restart identity;
truncate table transformationcache restart identity cascade;
truncate table transformation_cache restart identity cascade;
truncate table tokenstore restart identity;
truncate table date2serial restart identity;
truncate table published_schema_versions restart identity;
truncate table notification restart identity;
update factstream_checkpoint set fact_ser = 0, fact_id = null, notification_ser = 0 where id = 1;

select dropAllTailIndexes();












#
