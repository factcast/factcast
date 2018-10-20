/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.store.pgsql.internal;

import lombok.AccessLevel;
import lombok.Generated;
import lombok.experimental.FieldDefaults;

/**
 * String constants mainly used in SQL-Statement creation
 *
 * @author uwe.schaefer@mercateo.com
 *
 */
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
@Generated
public class PGConstants {

    public static String NEXT_FROM_CATCHUP_SEQ = "SELECT nextval('catchup_seq')";

    public static String TABLE_CATCHUP = "catchup";

    public static String TABLE_FACT = "fact";

    public static String CHANNEL_NAME = "fact_insert";

    public static String COLUMN_PAYLOAD = "payload";

    public static String COLUMN_HEADER = "header";

    public static String COLUMN_SER = "ser";

    public static String COLUMN_CID = "cid";

    public static String ALIAS_ID = "id";

    public static String ALIAS_NS = "ns";

    public static String ALIAS_TYPE = "type";

    public static String ALIAS_AGGID = "aggIds";

    public static String PROJECTION_FACT = String.join(", ", COLUMN_SER, COLUMN_HEADER,
            COLUMN_PAYLOAD,
            fromHeader(ALIAS_ID), fromHeader(ALIAS_AGGID), fromHeader(ALIAS_NS), fromHeader(
                    ALIAS_TYPE));

    public static String PROJECTION_ID = String.join(", ", COLUMN_SER, empty(COLUMN_HEADER), empty(
            COLUMN_PAYLOAD), fromHeader(ALIAS_ID), fromHeader(ALIAS_AGGID), fromHeader(ALIAS_NS),
            fromHeader(ALIAS_TYPE));

    public static String INSERT_FACT = "INSERT INTO " + TABLE_FACT + "(" + COLUMN_HEADER + ","
            + COLUMN_PAYLOAD + ") VALUES (cast(? as jsonb),cast (? as jsonb))";

    public static String SELECT_BY_ID = "SELECT " + PROJECTION_FACT + " FROM " + TABLE_FACT
            + " WHERE "
            + COLUMN_HEADER + " @> cast (? as jsonb)";

    public static String SELECT_LATEST_SER = "SELECT max(" + COLUMN_SER + ") FROM " + TABLE_FACT;

    public static String SELECT_ID_FROM_CATCHUP = //
            "SELECT " + PROJECTION_ID + //
                    " FROM " + TABLE_FACT + //
                    " WHERE " + COLUMN_SER + " IN ( " + //
                    "   SELECT " + COLUMN_SER + " FROM " + TABLE_CATCHUP + //
                    "   WHERE ( " + COLUMN_CID + "=? AND " + COLUMN_SER + ">? ) LIMIT ? " + //
                    ") ORDER BY " + COLUMN_SER + " ASC";

    public static String SELECT_FACT_FROM_CATCHUP = //
            "SELECT " + PROJECTION_FACT + //
                    " FROM " + TABLE_FACT + //
                    " WHERE " + COLUMN_SER + " IN ( " + //
                    "   SELECT " + COLUMN_SER + " FROM " + TABLE_CATCHUP + //
                    "   WHERE ( " + COLUMN_CID + "=? AND " + COLUMN_SER + ">? ) LIMIT ? " + //
                    ") ORDER BY " + COLUMN_SER + " ASC";

    public static String DELETE_CATCH_BY_CID = "DELETE FROM " + TABLE_CATCHUP + //
            " WHERE cid=?";

    public static final String SELECT_BY_HEADER_JSON = "SELECT " + COLUMN_SER + " FROM "
            + TABLE_FACT + " WHERE " + COLUMN_HEADER + " @> ?::jsonb";

    public static final String LISTEN_SQL = "LISTEN " + CHANNEL_NAME;

    public static final String UPDATE_FACT_SERIALS = "update " + TABLE_FACT + " set "
            + COLUMN_HEADER + "= jsonb_set( " + COLUMN_HEADER
            + " , '{meta}' , COALESCE(" + COLUMN_HEADER
            + "->'meta','{}') || concat('{\"_ser\":', " + COLUMN_SER
            + " ,'}' )::jsonb , true) WHERE header @> ?::jsonb";

    public static String SELECT_SER_BY_ID = "SELECT " + COLUMN_SER + " FROM " + TABLE_FACT
            + " WHERE "
            + COLUMN_HEADER + " @> cast (? as jsonb)";

    private static String fromHeader(String attributeName) {
        return PGConstants.COLUMN_HEADER + "->>'" + attributeName + "' AS " + attributeName;
    }

    private static String empty(String attributeName) {
        return "'{}' AS " + attributeName;
    }

}
