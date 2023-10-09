DROP TRIGGER IF EXISTS tr_fact_date2serial ON fact CASCADE; 
DROP FUNCTION IF EXISTS maintain_fact_date CASCADE;

CREATE TABLE IF NOT EXISTS tmp_fact_date_trigger (date date);
INSERT INTO tmp_fact_date_trigger VALUES (now());

CREATE FUNCTION maintain_fact_date() RETURNS trigger AS $$
BEGIN
    INSERT INTO date2serial VALUES (now()::date,NEW.ser,NEW.ser) ON CONFLICT(date) DO UPDATE SET lastSer = excluded.ser; 
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_fact_date2serial BEFORE INSERT ON fact FOR EACH ROW EXECUTE PROCEDURE maintain_fact_date();

