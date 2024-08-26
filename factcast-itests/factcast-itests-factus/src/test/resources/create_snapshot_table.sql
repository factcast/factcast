CREATE TABLE IF NOT EXISTS snapshots(
key VARCHAR(512),
uuid VARCHAR(36),
last_fact_id VARCHAR(36),
bytes BYTEA,
compressed boolean,
PRIMARY KEY (key, uuid));