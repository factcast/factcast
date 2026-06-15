-- only run this once a rollback is out of question
DROP TRIGGER IF EXISTS tr_deferred_blacklist_insert on blacklist;
DROP TRIGGER IF EXISTS tr_deferred_blacklist_update on blacklist;
DROP TRIGGER IF EXISTS tr_deferred_blacklist_delete on blacklist;
DROP TRIGGER IF EXISTS tr_deferred_blacklist_truncate on blacklist;

DROP FUNCTION notifyBlacklistChange();

DROP table blacklist;

