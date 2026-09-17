CREATE TABLE IF NOT EXISTS factstream_checkpoint
(
    id               SMALLINT PRIMARY KEY CHECK (id = 1),
    fact_ser         BIGINT NOT NULL,
    fact_id          UUID,
    notification_ser BIGINT NOT NULL
);

INSERT INTO factstream_checkpoint(id, fact_ser, fact_id, notification_ser)
VALUES (1, 0, NULL, 0)
ON CONFLICT (id) DO NOTHING;
