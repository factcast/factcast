-- Keeps fact.exclusion_reason in sync when a new blacklist entry is inserted.
create or replace function syncBlacklistChange() returns trigger
    language plpgsql
as
$$
BEGIN
    UPDATE fact
    SET exclusion_reason = COALESCE(NEW.reason, 'excluded without reason')
    WHERE (header ->> 'id')::uuid = NEW.id;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_sync AFTER INSERT ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE syncBlacklistChange();
