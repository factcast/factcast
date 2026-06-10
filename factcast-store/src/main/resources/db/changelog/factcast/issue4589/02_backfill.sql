UPDATE transformationcache
SET created_at = COALESCE(last_access, now());

UPDATE transformationcache
SET fact_id = substring(cache_key, 1, 36)::uuid
WHERE fact_id IS NULL;

ALTER TABLE transformationcache
    ALTER COLUMN fact_id SET NOT NULL;