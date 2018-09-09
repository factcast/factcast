--liquibase uniqueConstraintForIdent sql
--changeset usr:issue82 
create unique index unique_metaident on
   fact ((header->'meta'->'unique_identifier')) where (header->'meta'->'unique_identifier') notnull;


