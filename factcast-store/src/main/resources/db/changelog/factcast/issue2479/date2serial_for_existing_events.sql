-- add an entry with the first ser for every day
-- this should be run manually if you have a big fact table already, 
-- as it can take some time depending on your DB performance

INSERT INTO date2serial 
	(SELECT	fact_date(header) AS d, min(ser), max(ser) FROM fact GROUP BY d);


-- for the day when the trigger was introduced, we might have a wrong serial 
-- (not the first of the day). lets fix this:

DO 
$$
DECLARE 
  tofix date;
  startFrom bigint;
BEGIN
 
 	tofix := (SELECT LEAST((SELECT date FROM tmp_fact_date_trigger),now()));
  startFrom := (SELECT GREATEST((SELECT max(lastSer) FROM date2serial WHERE	date < tofix),0));
 
  INSERT INTO date2serial 
  	(SELECT fact_date(header), min(ser), max(ser) FROM fact WHERE fact_date(header) = tofix AND ser > startFrom GROUP BY fact_date(header)) 
  	ON CONFLICT(date) DO UPDATE 
  		SET firstSer=excluded.firstSer, lastSer=excluded.lastSer;
 
  DELETE FROM tmp_fact_date_trigger;
END; 
$$
;

DROP TABLE IF EXISTS tmp_fact_date_trigger;
