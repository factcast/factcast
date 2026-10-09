-- Recalculate the horizon after all in-flight publishers have committed or rolled back.
-- Keep this lock and the insert in the same Liquibase transaction.
SELECT pg_advisory_xact_lock(128);

INSERT INTO factstream_horizon(id, fact_ser, fact_id, notification_ser)
SELECT 1,
       COALESCE(latest_fact.ser, 0),
       latest_fact.id,
       COALESCE((SELECT MAX(ser) FROM notification), 0)
FROM (SELECT 1) singleton
         LEFT JOIN LATERAL (
    SELECT ser, (header ->> 'id')::UUID AS id
    FROM fact
    ORDER BY ser DESC
    LIMIT 1
    ) latest_fact ON TRUE
ON CONFLICT (id) DO UPDATE
    SET fact_ser = EXCLUDED.fact_ser,
        fact_id = EXCLUDED.fact_id,
        notification_ser = GREATEST(factstream_horizon.notification_ser, EXCLUDED.notification_ser);
