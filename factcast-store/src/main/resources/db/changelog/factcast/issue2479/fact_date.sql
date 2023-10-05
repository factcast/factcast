-- extracts a date FROM a fact header
-- this method is fairly expensive in terms of runtime so that 
-- it will only be used for migration of existing facts
CREATE OR REPLACE FUNCTION fact_date(header JSONB)
RETURNS date AS $$
BEGIN
	-- __ts needs to be divided by 1000 because it is set by java with a finer granularity
    RETURN to_timestamp( ($1 -> 'meta' ->> '_ts') :: numeric / 1000 ) :: date;
END;
$$ LANGUAGE plpgsql IMMUTABLE STRICT;