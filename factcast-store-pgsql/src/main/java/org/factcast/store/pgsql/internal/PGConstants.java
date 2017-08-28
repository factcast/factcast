package org.factcast.store.pgsql.internal;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;

/**
 * String constants mainly used in SQL-Statement creation
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@UtilityClass
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class PGConstants {

    public String NEXT_FROM_CATCHUP_SEQ = "SELECT nextval('catchup_seq')";

    public String TABLE_CATCHUP = "catchup";

    public String TABLE_FACT = "fact";

    public String CHANNEL_NAME = "fact_insert";

    public String COLUMN_PAYLOAD = "payload";

    public String COLUMN_HEADER = "header";

    public String COLUMN_SER = "ser";

    public String COLUMN_CID = "cid";

    public String ALIAS_ID = "id";

    public String ALIAS_NS = "ns";

    public String ALIAS_TYPE = "type";

    public String ALIAS_AGGID = "aggIds";

    public String PROJECTION_FACT = String.join(", ", COLUMN_SER, COLUMN_HEADER, COLUMN_PAYLOAD,
            fromHeader(ALIAS_ID), fromHeader(ALIAS_AGGID), fromHeader(ALIAS_NS), fromHeader(
                    ALIAS_TYPE));

    public String PROJECTION_ID = String.join(", ", COLUMN_SER, empty(COLUMN_HEADER), empty(
            COLUMN_PAYLOAD), fromHeader(ALIAS_ID), fromHeader(ALIAS_AGGID), fromHeader(ALIAS_NS),
            fromHeader(ALIAS_TYPE));

    public String INSERT_FACT = "INSERT INTO " + TABLE_FACT + "(" + COLUMN_HEADER + ","
            + COLUMN_PAYLOAD + ") VALUES (cast(? as jsonb),cast (? as jsonb))";

    public String SELECT_BY_ID = "SELECT " + PROJECTION_FACT + " FROM " + TABLE_FACT + " WHERE "
            + COLUMN_HEADER + " @> cast (? as jsonb)";

    public String SELECT_LATEST_SER = "SELECT max(" + COLUMN_SER + ") FROM " + TABLE_FACT;

    public String SELECT_ID_FROM_CATCHUP = //
            "SELECT " + PROJECTION_ID + //
                    " FROM " + TABLE_FACT + //
                    " WHERE " + COLUMN_SER + " IN ( " + //
                    "   SELECT " + COLUMN_SER + " FROM " + TABLE_CATCHUP + //
                    "   WHERE ( " + COLUMN_CID + "=? AND " + COLUMN_SER + ">? ) LIMIT ? " + //
                    ") ORDER BY " + COLUMN_SER + " ASC";

    public String SELECT_FACT_FROM_CATCHUP = //
            "SELECT " + PROJECTION_FACT + //
                    " FROM " + TABLE_FACT + //
                    " WHERE " + COLUMN_SER + " IN ( " + //
                    "   SELECT " + COLUMN_SER + " FROM " + TABLE_CATCHUP + //
                    "   WHERE ( " + COLUMN_CID + "=? AND " + COLUMN_SER + ">? ) LIMIT ? " + //
                    ") ORDER BY " + COLUMN_SER + " ASC";

    public String DELETE_CATCH_BY_CID = "DELETE FROM " + TABLE_CATCHUP + //
            " WHERE cid=?";

    public static final String SELECT_BY_HEADER_JSON = "SELECT " + COLUMN_SER + " FROM "
            + TABLE_FACT + " WHERE " + COLUMN_HEADER + " @> ?::jsonb";

    public static final String LISTEN_SQL = "LISTEN " + CHANNEL_NAME;

    public static final String UPDATE_FACT_SERIALS = "update " + TABLE_FACT + " set "
            + COLUMN_HEADER + "= jsonb_set( " + COLUMN_HEADER
            + " , '{meta}' , COALESCE(" + COLUMN_HEADER
            + "->'meta','{}') || concat('{\"_ser\":', " + COLUMN_SER
            + " ,'}' )::jsonb , true) WHERE header @> ?::jsonb";

    public String SELECT_SER_BY_ID = "SELECT " + COLUMN_SER + " FROM " + TABLE_FACT + " WHERE "
            + COLUMN_HEADER + " @> cast (? as jsonb)";

    private String fromHeader(String attributeName) {
        return PGConstants.COLUMN_HEADER + "->>'" + attributeName + "' AS " + attributeName;
    }

    private String empty(String attributeName) {
        return "'{}' AS " + attributeName;
    }

}
