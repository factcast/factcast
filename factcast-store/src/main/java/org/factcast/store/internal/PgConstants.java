/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal;

import java.util.Random;
import lombok.AccessLevel;
import lombok.Generated;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * String constants mainly used in SQL-Statement creation
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
@Generated
public class PgConstants {

  public static final String CURRENT_TIME_MILLIS = "SELECT TRUNC(EXTRACT(EPOCH FROM now()) * 1000)";

  public static final String TABLE_CATCHUP = "catchup";

  public static final String TABLE_FACT = "fact";
  public static final String TAIL_INDEX_NAME_PREFIX = "idx_fact_tail_";

  public static final String INDEX_NAME_COLUMN = "index_name";
  public static final String VALID_COLUMN = "valid";
  public static final String IS_VALID = "Y";
  public static final String IS_INVALID = "N";

  public static final String LIST_FACT_INDEXES_WITH_VALIDATION =
      "select "
          + INDEX_NAME_COLUMN
          + ", "
          + VALID_COLUMN
          + " from stats_index where tablename = '"
          + TABLE_FACT
          + "' and "
          + INDEX_NAME_COLUMN
          + " like '"
          + TAIL_INDEX_NAME_PREFIX
          + "%' order by "
          + INDEX_NAME_COLUMN
          + " desc";

  public static final String BROKEN_INDEX_NAMES =
      "SELECT "
          + INDEX_NAME_COLUMN
          + " FROM stats_index WHERE "
          + VALID_COLUMN
          + " = '"
          + IS_INVALID
          + "'";

  private static final String TABLE_TOKENSTORE = "tokenstore";

  public static final String CHANNEL_FACT_INSERT = "fact_insert";
  public static final String CHANNEL_SCHEDULED_POLL = "scheduled-poll";
  public static final String CHANNEL_BLACKLIST_CHANGE = "blacklist_change";
  public static final String CHANNEL_SCHEMASTORE_CHANGE = "schemastore_change";
  public static final String CHANNEL_TRANSFORMATIONSTORE_CHANGE = "transformationstore_change";
  public static final String CHANNEL_ROUNDTRIP =
      "roundtrip_channel_"
          + Math.abs(new Random().nextLong()); // using the pid lead to a sql exception

  public static final String COLUMN_PAYLOAD = "payload";

  public static final String COLUMN_HEADER = "header";

  public static final String COLUMN_VERSION = "version";

  public static final String COLUMN_SER = "ser";

  public static final String COLUMN_CID = "cid";

  private static final String COLUMN_STATE = "state";

  private static final String COLUMN_NAMESPACE = "ns";

  private static final String COLUMN_TOKEN = "token";

  public static final String ALIAS_ID = "id";

  public static final String ALIAS_NS = "ns";

  public static final String ALIAS_TYPE = "type";

  public static final String ALIAS_AGGID = "aggIds";

  private static final String ALIAS_VERSION = "version";

  public static final String PROJECTION_FACT =
      String.join(
          ", ",
          COLUMN_SER,
          COLUMN_HEADER,
          COLUMN_PAYLOAD,
          fromHeader(ALIAS_ID),
          fromHeader(ALIAS_AGGID),
          fromHeader(ALIAS_NS),
          fromHeader(ALIAS_TYPE),
          fromHeader(ALIAS_VERSION));

  public static final String INSERT_FACT =
      "INSERT INTO "
          + TABLE_FACT
          + "("
          + COLUMN_HEADER
          + ","
          + COLUMN_PAYLOAD
          + ") VALUES (cast(? as jsonb),cast (? as jsonb))";

  public static final String INSERT_TOKEN =
      "INSERT INTO "
          + TABLE_TOKENSTORE
          + " ("
          + COLUMN_STATE
          + ") VALUES (cast (? as jsonb)) RETURNING token";

  public static final String COMPACT_TOKEN =
      "DELETE FROM " + TABLE_TOKENSTORE + " WHERE extract(month from age(ts))>1";

  public static final String DELETE_TOKEN = "DELETE FROM " + TABLE_TOKENSTORE + " WHERE token=?";

  public static final String SELECT_BY_ID =
      "SELECT "
          + PROJECTION_FACT
          + " FROM "
          + TABLE_FACT
          + " WHERE "
          + COLUMN_HEADER
          + " @> cast (? as jsonb)";

  public static final //
  String SELECT_FACT_FROM_CATCHUP = //
      "SELECT "
          + PROJECTION_FACT
          + " FROM "
          + //
          TABLE_FACT
          + " WHERE "
          + COLUMN_SER
          + //
          " IN ( "
          + "   SELECT "
          + COLUMN_SER
          + " FROM "
          + //
          TABLE_CATCHUP
          + "   WHERE ( "
          + COLUMN_SER
          + //

          // the inner ORDER BY is important! see #1002

          ">? ) ORDER BY "
          + COLUMN_SER
          + " ASC LIMIT ? ) ORDER BY "
          + COLUMN_SER
          + " ASC";

  public static final //
  String SELECT_LATEST_FACTID_FOR_AGGID = //
      "SELECT "
          + COLUMN_HEADER
          + "->>'id' FROM "
          + //
          TABLE_FACT
          + " WHERE "
          + COLUMN_HEADER
          + //
          " @> cast (? as jsonb) ORDER BY ser DESC LIMIT 1";

  public static final String SELECT_BY_HEADER_JSON =
      "SELECT " + COLUMN_SER + " FROM " + TABLE_FACT + " WHERE " + COLUMN_HEADER + " @> ?::jsonb";

  public static final String LISTEN_SQL = "LISTEN " + CHANNEL_FACT_INSERT;

  public static final String NOTIFY_ROUNDTRIP = "NOTIFY " + CHANNEL_ROUNDTRIP;

  public static final String LISTEN_ROUNDTRIP_CHANNEL_SQL = "LISTEN " + CHANNEL_ROUNDTRIP;

  public static final String LISTEN_BLACKLIST_CHANGE_CHANNEL_SQL =
      "LISTEN " + CHANNEL_BLACKLIST_CHANGE;

  public static final String LISTEN_SCHEMASTORE_CHANGE_CHANNEL_SQL =
      "LISTEN " + CHANNEL_SCHEMASTORE_CHANGE;

  public static final String LISTEN_TRANSFORMATIONSTORE_CHANGE_CHANNEL_SQL =
      "LISTEN " + CHANNEL_TRANSFORMATIONSTORE_CHANGE;

  public static final String UPDATE_FACT_SERIALS =
      "update "
          + TABLE_FACT
          + " set "
          + COLUMN_HEADER
          + "= jsonb_set( "
          + COLUMN_HEADER
          + " , '{meta}' , COALESCE("
          + COLUMN_HEADER
          + "->'meta','{}') || concat('{\"_ser\":', "
          + COLUMN_SER
          + " ,', \"_ts\":', EXTRACT(EPOCH FROM now()::timestamptz(3))*1000, '}' )::jsonb , true)"
          + " WHERE header @> ?::jsonb";

  public static final String SELECT_DISTINCT_NAMESPACE =
      "SELECT DISTINCT("
          + COLUMN_HEADER
          + "->>'"
          + ALIAS_NS
          + "') "
          + ALIAS_NS
          + " FROM "
          + TABLE_FACT
          + " WHERE "
          + COLUMN_HEADER
          + "->>'"
          + ALIAS_NS
          + "' IS NOT NULL";

  public static final String SELECT_DISTINCT_TYPE_IN_NAMESPACE =
      "SELECT DISTINCT("
          + COLUMN_HEADER
          + "->>'"
          + ALIAS_TYPE
          + "') "
          + " FROM "
          + TABLE_FACT
          + " WHERE ("
          + COLUMN_HEADER
          + "->>'"
          + ALIAS_NS
          + "')=? AND ( "
          + COLUMN_HEADER
          + "->>'"
          + ALIAS_TYPE
          + "') IS NOT NULL";

  public static final String SELECT_SER_BY_ID =
      "SELECT "
          + COLUMN_SER
          + " FROM "
          + TABLE_FACT
          + " WHERE "
          + " (("
          + COLUMN_HEADER
          + "->>'"
          + ALIAS_ID
          + "')::uuid) = CAST(? as uuid)";

  public static final String SELECT_STATE_FROM_TOKEN =
      "SELECT " + COLUMN_STATE + " FROM " + TABLE_TOKENSTORE + " WHERE " + COLUMN_TOKEN + "=?";

  public static final String SELECT_NS_FROM_TOKEN =
      "SELECT " + COLUMN_NAMESPACE + " FROM " + TABLE_TOKENSTORE + " WHERE " + COLUMN_TOKEN + "=?";

  public static final String LAST_SERIAL_IN_LOG =
      "SELECT COALESCE(MAX(" + COLUMN_SER + "),0) from " + TABLE_FACT;
  public static final String HIGHWATER_MARK =
      "select ("
          + COLUMN_HEADER
          + "->>'"
          + ALIAS_ID
          + "')::uuid as targetId, ser as targetSer from "
          + TABLE_FACT
          + " where "
          + COLUMN_SER
          + "=(select max("
          + COLUMN_SER
          + ") from "
          + TABLE_FACT
          + ")";

  private static String fromHeader(String attributeName) {
    return PgConstants.COLUMN_HEADER + "->>'" + attributeName + "' AS " + attributeName;
  }

  public static String createTailIndex(String indexName, long ser) {
    return "create index concurrently "
        + indexName
        + " on "
        + TABLE_FACT
        + " using GIN("
        + COLUMN_HEADER
        + " jsonb_path_ops) WITH (gin_pending_list_limit = 16384 , fastupdate = true)  WHERE "
        + COLUMN_SER
        + ">"
        + ser;
  }

  @NonNull
  public static String tailIndexName(long epoch) {
    return TAIL_INDEX_NAME_PREFIX + epoch;
  }

  public static String dropTailIndex(String indexName) {
    return "DROP INDEX CONCURRENTLY IF EXISTS " + indexName;
  }

  public static String setStatementTimeout(long millis) {
    return "set statement_timeout to " + millis;
  }
}
