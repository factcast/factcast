create index concurrently
    idx_fact_idlookup on fact using hash
    (((header ->> 'id')::uuid));
