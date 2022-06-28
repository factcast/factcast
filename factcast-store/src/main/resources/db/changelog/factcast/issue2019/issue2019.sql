-- first delete all duplicate transformations, that result from formerly synthetic ones.
delete from transformationstore where
        (ns,type,from_version,to_version) in
        (
            select ns,type,from_version,to_version
            from transformationstore
            group by ns,type,from_version,to_version
            having count(*) > 1
        ) and hash='none';

-- then create a unique index that prevents regression
create unique index CONCURRENTLY IF NOT EXISTS unique_ns_type_source_target on transformationstore (ns,type,from_version,to_version);
