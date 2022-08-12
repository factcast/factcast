-- make sure those will be in the main table and uncompressed
alter table transformationcache alter column cache_key set storage main;
alter table transformationcache alter column last_access set storage plain;

-- encourage externalizing them
alter table transformationcache alter column payload set storage extended;
alter table transformationcache alter column header  set storage extended;

-- set the target low, so that non-trivial headers and every payload will be TOASTed
alter table transformationcache set (toast_tuple_target = 256);
