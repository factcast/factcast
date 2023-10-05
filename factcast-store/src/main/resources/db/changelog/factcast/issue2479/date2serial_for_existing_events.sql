-- add an entry with the first ser for every day
-- this should be run manually if you have a big fact table already, 
-- as it can take some time depending on your DB performance

INSERT INTO date2serial 
	(SELECT	fact_date(header),ser FROM fact ORDER BY ser) 
ON CONFLICT(date) DO NOTHING;

-- for the day when the trigger was introduced, we might have a wrong serial 
-- (not the first of the day). lets fix this:

DO 
$$
DECLARE 
  tofix date;
  startFrom bigint;
  firstSer bigint;
BEGIN
 
 	tofix := (SELECT LEAST((SELECT date FROM tmp_fact_date_trigger),now()));
  startFrom := (SELECT GREATEST((SELECT max(ser) FROM date2serial WHERE	date < tofix),0));
  firstSer := (SELECT min(ser) FROM fact WHERE ser > startFrom AND fact_date(header) = tofix);
 
  INSERT INTO date2serial 
  	VALUES (tofix,firstSer) 
  	ON CONFLICT(date) DO UPDATE 
  	SET ser=excluded.ser;
 
  DELETE FROM tmp_fact_date_trigger;
END; 
$$
;

DROP TABLE IF EXISTS tmp_fact_date_trigger;
