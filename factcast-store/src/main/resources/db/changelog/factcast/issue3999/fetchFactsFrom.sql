CREATE OR REPLACE FUNCTION fetchFactsFrom(cur refcursor)
    RETURNS TABLE
            (
                ser     bigint,
                payload jsonb,
                header  jsonb
            )
    LANGUAGE plpgsql
AS
$$
DECLARE
    serialChunk bigint[];
BEGIN
    FETCH NEXT FROM fetchFactsFrom.cur INTO serialChunk;
    RETURN QUERY SELECT f.ser     as ser,
                        f.payload as payload,
                        f.header  as header
                 FROM fact f
                 WHERE f.ser = ANY (serialChunk)
                 ORDER BY f.ser;
END;
$$;
