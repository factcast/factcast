--liquibase removeUniqueConstraintForIdent sql
--changeset usr:issue346
drop index if exists unique_metaident;


