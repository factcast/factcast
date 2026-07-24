-- New transformation cache table keyed by (fact_id, version, path).
-- Created empty so it never locks or copies the (potentially huge) old
-- transformationcache table on startup. The old table is left untouched to
-- keep rollback trivial.
CREATE TABLE IF NOT EXISTS transformation_cache(
    fact_id    uuid                     NOT NULL,
    version    int                      NOT NULL,
    path       text COLLATE "C"         NOT NULL,
    header     jsonb                    NOT NULL,
    payload    jsonb                    NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (fact_id, version, path)
);

-- Mirror the TOAST/storage tuning of the original table (issue2131/issue1161):
-- keep headers in the main table, encourage externalizing payloads.
ALTER TABLE transformation_cache
    ALTER COLUMN header SET STORAGE MAIN,
    ALTER COLUMN payload SET STORAGE EXTENDED;

ALTER TABLE transformation_cache SET (toast_tuple_target = 256);
