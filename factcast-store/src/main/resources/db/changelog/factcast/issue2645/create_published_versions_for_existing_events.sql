-- add an entry with the first and last ser for every day
-- this should be run manually if you have a big fact table already,
-- as it can take some time depending on your DB performance

INSERT INTO published_schema_versions
    (SELECT header ->> 'ns'                     AS ns,
            header ->> 'type'                   AS type,
            cast((header ->> 'version') AS INT) as version
     FROM fact
     GROUP BY ns, type, version)
ON CONFLICT DO NOTHING;