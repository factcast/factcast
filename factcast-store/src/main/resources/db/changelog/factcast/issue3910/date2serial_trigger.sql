CREATE OR REPLACE FUNCTION maintain_fact_date() RETURNS trigger AS
$$
BEGIN
    INSERT INTO date2serial(factdate, firstser)
    VALUES (now()::date, NEW.ser)
    ON CONFLICT(factDate) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
