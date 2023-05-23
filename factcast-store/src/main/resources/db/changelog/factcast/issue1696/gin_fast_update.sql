-- increase the limit for the pending list in order to make inserts quicker
alter index idx_fact_header set (gin_pending_list_limit = 32768, fastupdate = true);
