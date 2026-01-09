-- with a batch of 100k and a sleep of 100ms between batches, we expect to migrate 1mil records within a few seconds
CALL migrate_transformationcache_batch(100000);
