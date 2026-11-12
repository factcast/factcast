-- Migrates the existing blacklist into the new fact.exclusion_reason column.
--
-- Runs in batches over the (PK-indexed) blacklist table and updates the matching
-- facts via the fact id.
-- Each batch commits in its own transaction and the loop throttles with pg_sleep, so the
-- migration never holds a long-running transaction or a table-level lock on fact.
-- The exclusion_reason is copied per fact from blacklist.reason; entries without a reason fall back
-- to 'excluded without reason' so that exclusion_reason IS NOT NULL reliably marks an excluded fact.
-- The update is idempotent/resumable (only touches rows still NULL), so re-running is safe.
CREATE OR REPLACE PROCEDURE migrate_blacklist_to_exclusion_reason(batch_size integer DEFAULT 10000)
LANGUAGE plpgsql
AS $$
DECLARE
    -- "minimum" uuid so that processing in order works
    v_last_id uuid := '00000000-0000-0000-0000-000000000000';
    v_ids     uuid[];
    v_rows    int;
BEGIN
    LOOP
        -- next batch of blacklisted ids, keyset-paginated by the blacklist primary key
        SELECT array_agg(id ORDER BY id)
        INTO v_ids
        FROM (
            SELECT id
            FROM blacklist
            WHERE id > v_last_id
            ORDER BY id
            LIMIT batch_size
        ) b;

        EXIT WHEN v_ids IS NULL;

        UPDATE fact f
        SET exclusion_reason = COALESCE(b.reason, 'excluded without reason')
        FROM blacklist b
        WHERE b.id = ANY (v_ids)
          AND (f.header ->> 'id')::uuid = b.id
          AND f.exclusion_reason IS NULL;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        RAISE NOTICE 'Excluded % facts', v_rows;

        v_last_id := v_ids[array_length(v_ids, 1)];

        COMMIT;

        -- throttling
        PERFORM pg_sleep(0.1);
    END LOOP;
END;
$$;
