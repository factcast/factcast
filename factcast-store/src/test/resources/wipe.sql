truncate table fact restart identity;
truncate table blacklist restart identity;
truncate table schemastore restart identity;
truncate table transformationstore restart identity;
truncate table transformationcache restart identity;
truncate table tokenstore restart identity;
truncate table date2serial restart identity;
truncate table publishedschemaversions restart identity;

select dropAllTailIndexes();










#
