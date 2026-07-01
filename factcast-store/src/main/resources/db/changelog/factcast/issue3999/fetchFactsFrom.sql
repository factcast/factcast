-- The cursor cur is supposed to contain just one column of type bigint[] that
-- contains all the serials of the facts to fetch in one function call.
-- Those facts are then fetched and returned as a table, unless the cursor is exhausted.
--
-- See CHUNKED_WIH_HOLD Catchup Strategy
--
CREATE OR REPLACE FUNCTION fetchFactsFromCursorWithHold(cur refcursor)
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
    FETCH NEXT FROM fetchFactsFromCursorWithHold.cur INTO serialChunk;
    RETURN QUERY SELECT f.ser     as ser,
                        f.payload as payload,
                        f.header  as header
                 FROM fact f
                 WHERE f.ser = ANY (serialChunk)
                 ORDER BY f.ser;
END;
$$;
