-- make sure those will be in the main table and uncompressed
alter table snapshot_cache alter column cache_key set storage main;

-- encourage externalizing them
alter table snapshot_cache alter column data set storage extended;

-- set the target low, so that non-trivial headers and every payload will be TOASTed
alter table snapshot_cache set (toast_tuple_target = 256);
