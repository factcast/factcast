-- with a batch of 10k blacklist ids and a 100ms sleep between batches, this stays
-- well below any lock thresholds while only touching the (typically few) excluded facts.
CALL migrate_blacklist_to_exclusion_reason(10000);