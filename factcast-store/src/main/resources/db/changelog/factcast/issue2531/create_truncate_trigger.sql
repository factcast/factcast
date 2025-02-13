-- this is necessary as in integration tests, the fact table may be truncated.
-- in that case, the factStore needs to know, in order to clear up transient state
-- (Deduplication ids in its Event bus for instance,
--  see org.factcast.store.internal.DeduplicatingEventBus)
CREATE OR REPLACE FUNCTION notifyTruncateForIntegrationTests() RETURNS trigger AS $$
BEGIN
  PERFORM pg_notify('fact_truncate', json_build_object(
    'txId', txid_current()
  )::text);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER fact_truncate
 BEFORE TRUNCATE ON fact
 FOR EACH STATEMENT
  EXECUTE FUNCTION notifyTruncateForIntegrationTests();

