CREATE FUNCTION notifyFactInsert() RETURNS trigger AS $$
BEGIN
  PERFORM pg_notify('fact_insert', json_build_object(
    'ser', NEW.ser,
    'header',NEW.header
  )::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_fact_insert AFTER INSERT ON fact FOR EACH ROW EXECUTE PROCEDURE notifyFactInsert();

