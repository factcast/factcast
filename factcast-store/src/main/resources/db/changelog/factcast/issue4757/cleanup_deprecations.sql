CREATE OR REPLACE PROCEDURE cleanup_issue4757_notify_per_insert(
    passphrase text DEFAULT ''
) AS
$$
BEGIN
    IF passphrase = 'do it damnit' THEN

        DROP TRIGGER IF EXISTS tr_deferred_fact_insert on fact CASCADE;
        DROP FUNCTION IF EXISTS notifyfactinsert;

        RAISE INFO '';
        RAISE INFO 'Cleanup done. Now call:';
        RAISE INFO '';
        RAISE INFO 'DROP PROCEDURE cleanup_issue4757_notify_per_insert;';
        RAISE INFO '';

    ELSE
        RAISE WARNING 'Warning:';
        RAISE WARNING '';
        RAISE WARNING 'This procedure is supposed to be used, in order to drop resources that became deprecated with';
        RAISE WARNING 'the change of notification mechanism in version 0.11.3';
        RAISE WARNING '';
        RAISE WARNING 'If you still have a factcast server <0.11.3 running, or wish to be able to fall back to one,';
        RAISE WARNING 'do not run this procedure.';
        RAISE WARNING '';
        RAISE WARNING 'If you know what you are doing: ';
        RAISE WARNING '';
        RAISE WARNING 'CALL cleanup_issue4757_notify_per_insert(''do it damnit'');';
        RAISE WARNING '';

    END IF;

END;
$$ LANGUAGE plpgsql;
