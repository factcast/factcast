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
      cache_key VARCHAR(2048) NOT NULL, -- note that the UUID is part of thePRIMARY KEY,
      so it IS NOT nullable. -- while it makes sense to use a

      separate uuid
      WHEN storing aggregate states,
      it IS -- completely useless
WITH projection states. IN this CASE
        we store a fake uuid(
          0,
          0
        ) IN ORDER TO maintain the PK CONSTRAINT uuid uuid NOT NULL, -- represents the stateOF the BLOB(
        processed ALL relevant facts up TO factid
    ) factid uuid NOT NULL,
    DATA bytea NOT NULL, -- indicated if the data is already compressedIN ORDER TO bypass transport compression IF possible compressed BOOLEAN NOT NULL,
    last_access TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    PRIMARY KEY(
      uuid,
      cache_key
    ));

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
