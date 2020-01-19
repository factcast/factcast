--liquibase addSerialForPG11Compatibility sql
--changeset usr:issue601


--- liquibase has a problem with creating bigserial columns when defined in yml against postgres>9.6
--- therefore it is necessary to make sure, that a sequence was properly created and the fact table's column ser
--- has a proper default.

create sequence if not exists fact_ser_seq;
alter table fact alter column ser set default nextval('fact_ser_seq');

--- we currently cannot imagine an upgrade/migration scenario that makes this necessary,
--- but just for precautions sake, we set a newly created sequence's value

SELECT setval('fact_ser_seq', max(ser)+100) FROM fact;
