-- The singleton horizon bounds safe fact-stream reads.
CREATE TABLE IF NOT EXISTS factstream_horizon
(
    id               SMALLINT PRIMARY KEY CHECK (id = 1),
    fact_ser         BIGINT NOT NULL,
    fact_id          UUID,
    notification_ser BIGINT NOT NULL
);

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
ON CONFLICT (id) DO NOTHING;
