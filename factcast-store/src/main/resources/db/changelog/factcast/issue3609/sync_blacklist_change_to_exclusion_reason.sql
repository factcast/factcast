-- Keeps fact.exclusion_reason in sync with the legacy blacklist table for as long as both exist.
--
-- Deliberately row-level only: TRUNCATE (and DROP TABLE) must NOT clear exclusion_reason, so there
-- is no AFTER TRUNCATE trigger here (unlike notifyBlacklistChange) -- see remove_blacklist.sql for
-- the teardown, which drops this trigger before dropping the table.
create or replace function syncBlacklistChange() returns trigger
    language plpgsql
as
$$
BEGIN
    -- entry removed from the blacklist -> the fact is not excluded anymore
    IF TG_OP = 'DELETE' THEN
        UPDATE fact
        SET exclusion_reason = NULL
        WHERE (header ->> 'id')::uuid = OLD.id;
        RETURN OLD;
    END IF;

    -- an UPDATE may in theory re-point the entry to a different fact, un-exclude the previous one
    IF TG_OP = 'UPDATE' AND OLD.id <> NEW.id THEN
        UPDATE fact
        SET exclusion_reason = NULL
        WHERE (header ->> 'id')::uuid = OLD.id;
    END IF;

    UPDATE fact
    SET exclusion_reason = COALESCE(NEW.reason, 'excluded without reason')
    WHERE (header ->> 'id')::uuid = NEW.id;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_deferred_blacklist_sync ON blacklist;

CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_sync AFTER INSERT OR UPDATE OR DELETE ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE syncBlacklistChange();