CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fact_header_active
    ON fact USING GIN (header jsonb_path_ops)
    WHERE exclusion_reason IS NULL;

-- replicate configuration from issue3755
ALTER INDEX idx_fact_header_active SET ( fastupdate = false )
