-- fix the index index_for_enum from issue170
create index concurrently if not exists index_for_enumeration on fact using btree((header->>'ns'),(header->>'type'));
drop index if exists index_for_enum;
