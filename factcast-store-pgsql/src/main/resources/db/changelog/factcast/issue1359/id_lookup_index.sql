create index concurrently
    idx_fact_idlookup on fact
    (((header ->> 'id')::uuid), ser)
