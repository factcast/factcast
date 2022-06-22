CREATE
    OR REPLACE FUNCTION dropAllTailIndexes()
    RETURNS void
AS
$$
DECLARE
    i varchar;
BEGIN
    FOR i IN select index_name from stats_index where tablename='fact' and index_name like 'idx_fact_tail_%'
        LOOP
            DROP INDEX i;
        END LOOP;
END;
$$
    LANGUAGE plpgsql;
