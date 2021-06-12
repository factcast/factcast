-- must not be created concurrently, as we may end up with duplicates otherwise
create unique index idx_fact_unique_uuid on fact (((header ->> 'id') :: uuid));

drop index idx_fact_unique_id;
