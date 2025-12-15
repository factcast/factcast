-- with a batch of 10k and a sleep of 10ms between batches, we expect to migrate 1mil records within a few seconds
CALL migrate_transformationcache_batch(10000);
