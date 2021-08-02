--liquibase nonNullConstraintForNS sql
--changeset usr:issue145 
update fact set header=jsonb_set(header ,'{ns}' ,'"default"'::jsonb, true) where (header->'ns') is null;
alter table fact add constraint mandatory_ns CHECK (header ?? 'ns');
alter table fact add constraint mandatory_id CHECK (header ?? 'id');
