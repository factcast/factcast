DROP
  TRIGGER IF EXISTS tr_fact_date2serial ON
  fact CASCADE;

DROP
  FUNCTION IF EXISTS maintain_fact_date CASCADE;

CREATE
  FUNCTION maintain_fact_date() RETURNS TRIGGER AS $$ DECLARE minSer BIGINT;

maxSer BIGINT;

now DATE;

BEGIN -- should probably be replaced by a pg_cron job
SELECT
  MIN( ser ),
  MAX( ser ),
  now()::date
FROM
  new_rows INTO
    minSer,
    maxSer,
    now;

INSERT
  INTO
    date2serial
  VALUES(
    now,
    minSer,
    maxSer
  ) ON
  CONFLICT(factDate) DO UPDATE
  SET
    lastSer = greatest(
      maxSer,
      excluded.lastser
    );

RETURN NULL;
END;

$$ LANGUAGE plpgsql;

CREATE
  TRIGGER tr_fact_date2serial AFTER INSERT
    ON
    fact REFERENCING NEW TABLE
      new_rows FOR EACH STATEMENT EXECUTE FUNCTION maintain_fact_date();
