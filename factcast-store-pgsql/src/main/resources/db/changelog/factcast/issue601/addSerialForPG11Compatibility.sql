--liquibase addSerialForPG11Compatibility sql
--changeset usr:issue601
create sequence if not exists fact_ser_seq;
SELECT setval('fact_ser_seq', max(ser)+100) FROM fact;
alter table fact alter column ser set default nextval('fact_ser_seq');


