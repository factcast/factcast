alter table transformationcache_access
    ADD FOREIGN KEY (cache_key)
        REFERENCES transformationcache (cache_key) ON DELETE CASCADE;
