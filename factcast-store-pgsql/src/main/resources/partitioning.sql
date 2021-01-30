create or replace function repartition(numberOfPartitions bigint) RETURNS void AS $$
    begin
    FOR i IN  0..1000000
    LOOP
            insert into narf values (json_build_object('foo',10000000));
            insert into narf2 values ('{"foo":"10000000"}');
    END LOOP;
    end
    $$ language plpgsql;
