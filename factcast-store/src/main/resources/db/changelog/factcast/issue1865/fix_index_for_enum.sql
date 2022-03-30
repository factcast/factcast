-- fix the index index_for_enum from issue170
drop index if exists index_for_enum;
create index concurrently if not exists index_for_enum on fact using btree((header->>'ns'),(header->>'type'));
