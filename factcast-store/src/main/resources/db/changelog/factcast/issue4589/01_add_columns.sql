ALTER TABLE transformationcache
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL;

ALTER TABLE transformationcache
    ADD COLUMN fact_id uuid;
