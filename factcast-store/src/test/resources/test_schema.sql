KARPOTT;


CREATE
  EXTENSION IF NOT EXISTS "uuid-ossp";

DROP
  TRIGGER IF EXISTS tr_fact_insert ON
  fact;

DROP
  SEQUENCE IF EXISTS catchup_seq;

DROP
  TABLE
    IF EXISTS fact CASCADE;

DROP
  TABLE
    IF EXISTS catchup CASCADE;

DROP
  TABLE
    IF EXISTS schemastore CASCADE;

DROP
  TABLE
    IF EXISTS tokenstore CASCADE;

DROP
  TABLE
    IF EXISTS snapshot_cache CASCADE;

DROP
  TABLE
    IF EXISTS transformationstore CASCADE;

DROP
  TABLE
    IF EXISTS transformationcache CASCADE;

CREATE
  TABLE
    fact(
      ser SERIAL PRIMARY KEY,
      header JSONB NOT NULL,
      payload JSONB NOT NULL
    );

CREATE
  OR REPLACE FUNCTION notifyFactInsert() RETURNS TRIGGER AS $$ BEGIN PERFORM pg_notify(
    'fact_insert',
    json_build_object(
      'ser',
      NEW.ser,
      'header',
      NEW.header
    )::text
  );

RETURN NEW;
END;

$$ LANGUAGE plpgsql;

CREATE
  CONSTRAINT TRIGGER tr_deferred_fact_insert AFTER INSERT
    ON
    fact DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();

CREATE
  SEQUENCE catchup_seq;

CREATE
  TABLE
    catchup(
      cid BIGINT,
      ser BIGINT,
      ts TIMESTAMP
    );

CREATE
  TABLE
    IF NOT EXISTS tokenstore(
      token UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      ns VARCHAR,
      state JSONB NOT NULL,
      ts TIMESTAMP DEFAULT now()
    );

CREATE
  TABLE
    IF NOT EXISTS schemastore(
      id VARCHAR(2048) PRIMARY KEY,
      hash VARCHAR(32),
      ns VARCHAR(255),
      TYPE VARCHAR(255),
      version INT,
      jsonschema text,
      UNIQUE(id),
      UNIQUE(
        ns,
        TYPE,
        version
      )
    );

CREATE
  TABLE
    transformationstore(
      id VARCHAR(2048) PRIMARY KEY,
      hash VARCHAR(32),
      ns VARCHAR(255) NOT NULL,
      TYPE VARCHAR(255) NOT NULL,
      from_version INT NOT NULL,
      to_version INT NOT NULL,
      transformation text
    );

CREATE
  TABLE
    IF NOT EXISTS transformationcache(
      cache_key VARCHAR(2048) PRIMARY KEY,
      header text NOT NULL,
      payload text NOT NULL,
      last_access TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
    );

CREATE
  TABLE
    IF NOT EXISTS shedlock(
      name VARCHAR(64) NOT NULL,
      lock_until TIMESTAMP NOT NULL,
      locked_at TIMESTAMP NOT NULL,
      locked_by VARCHAR(255) NOT NULL,
      PRIMARY KEY(name)
    );

CREATE
  TABLE
    IF NOT EXISTS snapshot_cache(
      cache_key VARCHAR(2048) NOT NULL, -- note that the UUID is part of the primary key, so it is not nullable.-- while it makes sense to use a separate uuid when storing aggregate states, it is-- completely useless with projection states. In this case we store a fake uuid(0,0) in order to maintain the PK constraint
      uuid uuid NOT NULL, -- represents the state of the blob (processed all relevant facts up to factid)
      factid uuid NOT NULL,
      DATA bytea NOT NULL, -- indicated if the data is already compressed in order to bypass transport compression if possible
      compressed BOOLEAN NOT NULL,
      last_access TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
      PRIMARY KEY(
        uuid,
        cache_key
      )
    );

ALTER TABLE
  ONLY public.catchup ADD CONSTRAINT catchup_pkey PRIMARY KEY(
    cid,
    ser
  );

CREATE
  UNIQUE INDEX idx_fact_unique_uuid ON
  public.fact
    USING btree(
    (
      (
        (
          header ->> 'id'::text
        )
      )::uuid
    )
  );

CREATE
  INDEX idx_fact_header ON
  public.fact
    USING gin(
    header jsonb_path_ops
  );

CREATE
  INDEX idx_schemastore ON
  public.schemastore
    USING btree(
    ns,
    TYPE,
    version
  );

CREATE
  INDEX idx_tokenstore_ts ON
  public.tokenstore
    USING btree(ts);

CREATE
  INDEX idx_transformationstore ON
  public.transformationstore
    USING btree(
    ns,
    TYPE
  );

CREATE
  INDEX index_for_enum ON
  public.fact
    USING btree(
    (
      (
        header ->> 'ns'::text
      )
    ),
    (
      (
        header -> 'type'::text
      )
    )
  );

CREATE
  INDEX transformationcache_last_access ON
  public.transformationcache
    USING btree(last_access);

CREATE
  INDEX snapshot_cache_last_access ON
  snapshot_cache
    USING BTREE(last_access);

DROP
  VIEW IF EXISTS stats_index;

CREATE
  VIEW stats_index AS SELECT
    t.schemaname,
    t.tablename,
    c.reltuples::BIGINT AS num_rows,
    pg_size_pretty(
      pg_relation_size(c.oid)
    ) AS table_size,
    psai.indexrelname AS index_name,
    pg_size_pretty(
      pg_relation_size(i.indexrelid)
    ) AS index_size,
    CASE
      WHEN i.indisunique THEN 'Y'
      ELSE 'N'
    END AS "unique",
    psai.idx_scan AS number_of_scans,
    psai.idx_tup_read AS tuples_read,
    psai.idx_tup_fetch AS tuples_fetched,
    CASE
      WHEN i.indisvalid THEN 'Y'
      ELSE CASE
        WHEN i IS NULL THEN '-'
        ELSE 'N'
      END
    END AS "valid"
  FROM
    pg_tables t
  LEFT JOIN pg_class c ON
    t.tablename = c.relname
  LEFT JOIN pg_index i ON
    c.oid = i.indrelid
  LEFT JOIN pg_stat_all_indexes psai ON
    i.indexrelid = psai.indexrelid
  WHERE
    t.schemaname NOT IN(
      'pg_catalog',
      'information_schema'
    )
  ORDER BY
    1,
    2;

DROP
  TRIGGER IF EXISTS tr_fact_augment ON
  fact CASCADE;

DROP
  FUNCTION IF EXISTS augmentSerialAndTimestamp CASCADE;

CREATE
  FUNCTION augmentSerialAndTimestamp() RETURNS TRIGGER AS $$ BEGIN SELECT
    jsonb_set(
      NEW.header,
      '{meta}',
      COALESCE(
        NEW.header -> 'meta',
        '{}'
      )|| CONCAT(
        '{',
        '"_ser":',
        NEW.ser,
        ',',
        '"_ts":',
        EXTRACT(
          EPOCH
        FROM
          now()::timestamptz(3)
        )* 1000,
        '}'
      )::jsonb,
      TRUE
    ) INTO
      NEW.header;

RETURN NEW;
END;

$$ LANGUAGE plpgsql;

CREATE
  TRIGGER tr_fact_augment BEFORE INSERT
    ON
    fact FOR EACH ROW EXECUTE PROCEDURE augmentSerialAndTimestamp();



CREATE OR REPLACE FUNCTION notifyFactInsert() RETURNS trigger AS
$$
DECLARE
    notified BOOLEAN;
    ns varchar;
    type varchar;
BEGIN

    ns := NEW.header ->> 'ns';
    type := NEW.header ->> 'type';

    notified := NULLIF(current_setting(CONCAT('myvars.facttrigger.',ns,'.',type), TRUE), '');

    IF notified IS NULL THEN
        perform set_config(CONCAT('myvars.facttrigger.',ns,'.',type),'TRUE',TRUE);
        PERFORM pg_notify('fact_insert', json_build_object(
                'ser', NEW.ser,
            -- header is deprecated and will be removed
                'header', NEW.header,
                'txId', txid_current(),
                'ns',ns,
                'type',type
            )::text);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_deferred_fact_insert ON fact;
CREATE CONSTRAINT TRIGGER tr_deferred_fact_insert AFTER INSERT ON fact DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();

CREATE
    TABLE
    IF NOT EXISTS blacklist(
                              id UUID NOT NULL,
                              PRIMARY KEY(id)
);

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
DROP TRIGGER IF EXISTS tr_blacklist_truncate on blacklist;

CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_insert AFTER INSERT ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyBlacklistChange();
CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_update AFTER UPDATE ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyBlacklistChange();
CREATE CONSTRAINT TRIGGER tr_deferred_blacklist_delete AFTER DELETE ON blacklist DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE PROCEDURE notifyBlacklistChange();
CREATE TRIGGER tr_blacklist_truncate AFTER TRUNCATE ON blacklist FOR EACH STATEMENT EXECUTE PROCEDURE notifyBlacklistChange();



-- unfortunately, the masterminds behind spring desperately need this last 'separator character'. don't ask why...
-- just remove it, if you copy this to a console.

#
