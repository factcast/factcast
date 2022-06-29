create table if not exists blacklist (id uuid);

CREATE OR REPLACE FUNCTION notifyBlacklistChange() RETURNS trigger AS
$$
BEGIN
    PERFORM pg_notify('blacklist_change', json_build_object(
            'txId', txid_current()
        )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_deferred_blacklist_insert on blacklist;
DROP TRIGGER IF EXISTS tr_deferred_blacklist_update on blacklist;
DROP TRIGGER IF EXISTS tr_deferred_blacklist_delete on blacklist;
DROP TRIGGER IF EXISTS tr_deferred_blacklist_truncate on blacklist;

CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_insert AFTER INSERT ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyBlacklistChange();
CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_update AFTER UPDATE ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyBlacklistChange();
CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_delete AFTER DELETE ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyBlacklistChange();
CREATE TRIGGER tr_deferred_blacklist_truncate AFTER TRUNCATE ON blacklist FOR EACH STATEMENT EXECUTE PROCEDURE notifyBlacklistChange();

