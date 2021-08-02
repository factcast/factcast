alter table fact
    alter column header set storage MAIN;
alter table fact
    alter column payload set storage EXTENDED;
alter table transformationcache
    alter column header set storage MAIN;
alter table transformationcache
    alter column payload set storage EXTENDED;

-- also, as we're here, correct a few col types

alter table transformationcache
    alter column header type jsonb USING header::jsonb;
alter table transformationcache
    alter column payload type jsonb USING payload::jsonb;
alter table schemastore
    alter column jsonschema type jsonb using jsonschema::jsonb;


