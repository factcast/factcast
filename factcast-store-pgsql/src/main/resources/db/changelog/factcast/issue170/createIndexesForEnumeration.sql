--liquibase createIndexesForEnumeration sql
--changeset usr:issue170
create index index_for_enum on fact using btree((header->>'ns'),(header->'type'));

