-- New transformation cache table keyed by (fact_id, version).
-- Created empty so it never locks or copies the (potentially huge) old
-- transformationcache table on startup. The old table is left untouched to
-- keep rollback trivial.
CREATE TABLE IF NOT EXISTS transformationcache_v2(
    fact_id    uuid                     NOT NULL,
    version    int                      NOT NULL,
    header     jsonb                    NOT NULL,
    payload    jsonb                    NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (fact_id, version)
);

-- Mirror the TOAST/storage tuning of the original table (issue2131/issue1161):
-- keep headers in the main table, encourage externalizing payloads.
ALTER TABLE transformationcache_v2
    ALTER COLUMN header SET STORAGE MAIN,
    ALTER COLUMN payload SET STORAGE EXTENDED;

ALTER TABLE transformationcache_v2 SET (toast_tuple_target = 256);
