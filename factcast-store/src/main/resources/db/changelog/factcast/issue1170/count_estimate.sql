-- can be used for UIs to guess the count of a query swiftly
CREATE OR REPLACE FUNCTION count_estimate(query text) RETURNS bigint
    LANGUAGE plpgsql AS
$$
DECLARE
    plan jsonb;
BEGIN
    EXECUTE 'EXPLAIN (FORMAT JSON) ' || query INTO plan;
    RETURN (plan->0->'Plan'->>'Plan Rows')::bigint;
END;
$$;
