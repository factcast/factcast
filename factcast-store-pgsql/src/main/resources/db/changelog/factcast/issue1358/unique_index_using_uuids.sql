create unique index concurrently if not exists idx_fact_unique_uuid on fact (((header ->> 'id') :: uuid));
drop index idx_fact_unique_id;
