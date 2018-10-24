--liquibase formatted sql
--changeset usr:issue180
DROP TRIGGER IF EXISTS tr_fact_insert on fact;
DROP TRIGGER IF EXISTS tr_deferred_fact_insert on fact;
CREATE CONSTRAINT TRIGGER tr_deferred_fact_insert AFTER INSERT ON fact DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();

