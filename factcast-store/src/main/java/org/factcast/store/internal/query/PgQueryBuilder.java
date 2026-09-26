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
package org.factcast.store.internal.query;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.FactSpec;
import org.factcast.store.internal.PgConstants;
import org.springframework.jdbc.core.PreparedStatementSetter;

/**
 * Provides {@link PreparedStatementSetter} and the corresponding SQL from a list of {@link
 * FactSpec}s.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
public class PgQueryBuilder {

  private static final String ORDER_BY = " ORDER BY ";
  private static final String WHERE = " WHERE ";
  private static final String FROM = " FROM ";
  private static final String AND = " AND ";
  private static final String OR = " OR ";
  public static final String CONTAINS_JSONB = " @> ?::jsonb ";

  private final @NonNull Collection<FactSpec> factSpecs;
  private String tempTableName = null;
  private boolean serialsOnly = false;

  public PgQueryBuilder(@NonNull Collection<FactSpec> specs) {
    factSpecs = specs;
  }

  public PreparedStatementSetter createUnboundedStatementSetter(@NonNull AtomicLong serial) {
    return p -> setParameters(p, serial.get(), 0, OptionalLong.empty());
  }

  public PreparedStatementSetter createBoundedStatementSetter(
      @NonNull AtomicLong serial, long horizonSerial) {
    return p -> setParameters(p, serial.get(), 0, OptionalLong.of(horizonSerial));
  }

  /** Applies the same predicates to both branches of the state query. */
  public PreparedStatementSetter createStateStatementSetter(long serial) {
    return createStateStatementSetter(serial, OptionalLong.empty());
  }

  public PreparedStatementSetter createStateStatementSetter(
      long serial, @NonNull OptionalLong horizonSerial) {
    return p -> {
      int count = setParameters(p, serial, 0, horizonSerial);
      setParameters(p, serial, count, horizonSerial);
    };
  }

  private int setParameters(PreparedStatement p, long serial, int count, OptionalLong horizonSerial)
      throws SQLException {
    for (FactSpec spec : factSpecs) {
      count = setNs(p, count, spec);
      count = setType(p, count, spec);
      // version is intentionally not used here
      count = setAggIds(p, count, spec);
      count = setAggProperties(p, count, spec);
      count = setMeta(p, count, spec);
      count = setMetaKeyExists(p, count, spec);
    }
    p.setLong(++count, serial);
    if (horizonSerial.isPresent()) {
      p.setLong(++count, horizonSerial.getAsLong());
    }
    return count;
  }

  @SneakyThrows
  private int setAggProperties(PreparedStatement p, int count, FactSpec spec) {

    if (filterByAggregateIdProperty(spec)) {
      p.setInt(++count, spec.version());

      Set<Entry<String, UUID>> entries = spec.aggIdProperties().entrySet();
      // we need to make sure we have a stable sort order
      for (Entry<String, UUID> entry : entries) {
        p.setObject(++count, entry.getValue());
      }
    }
    return count;
  }

  private boolean filterByAggregateIdProperty(FactSpec spec) {
    // we can only apply filtering in the database if we know precisely what version to
    // look for, as otherwise the property might be modified by transformation
    return (spec.aggIdProperties() != null && !spec.aggIdProperties().isEmpty())
        && spec.version() != 0;
  }

  private String calculateJsonbExpressionFromPropertyPath(String key) {
    String path =
        Arrays.stream(key.split("\\.")).map(s -> "'" + s + "'").collect(Collectors.joining("."));
    String exp = "(payload -> " + path.replace(".", " -> ") + ")::UUID";
    return replaceLast(exp, "->", "->>");
  }

  public static String replaceLast(
      @NonNull String string, @NonNull String toReplace, @NonNull String replacement) {
    int pos = string.lastIndexOf(toReplace);
    if (pos > -1) {
      return string.substring(0, pos) + replacement + string.substring(pos + toReplace.length());
    } else {
      return string;
    }
  }

  private int setMeta(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    Map<String, String> meta = spec.meta();
    for (Entry<String, String> e : meta.entrySet()) {
      // single value
      p.setString(++count, "{\"meta\":{\"" + e.getKey() + "\":\"" + e.getValue() + "\"}}");
      // array
      p.setString(++count, "{\"meta\":{\"" + e.getKey() + "\":[\"" + e.getValue() + "\"]}}");
    }
    return count;
  }

  private int setMetaKeyExists(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    Map<String, Boolean> meta = spec.metaKeyExists();
    for (Entry<String, Boolean> e : meta.entrySet()) {
      String s = "$.\"meta\".\"" + e.getKey() + "\"";
      p.setString(++count, s);
    }
    return count;
  }

  private int setAggIds(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    if (filterByAggregateIds(spec)) {
      String a =
          spec.aggIds().stream()
              .map(UUID::toString)
              .collect(Collectors.joining("\",\"", "\"", "\""));
      p.setString(++count, "{\"aggIds\": [" + a + "]}");
    }
    return count;
  }

  private static boolean filterByAggregateIds(FactSpec specs) {
    return specs.aggIds() != null && !specs.aggIds().isEmpty();
  }

  private int setType(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    String type = spec.type();
    if (type != null && !"*".equals(type)) {
      p.setString(++count, "{\"type\": \"" + type + "\"}");
    }
    return count;
  }

  private int setNs(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    String ns = spec.ns();
    if (!"*".equals(ns)) {
      p.setString(++count, "{\"ns\": \"" + spec.ns() + "\"}");
    }
    return count;
  }

  @SuppressWarnings("java:S3776")
  private String createWhereClause(boolean bounded) {
    List<String> predicates = new LinkedList<>();
    factSpecs.forEach(
        spec -> {
          StringBuilder sb = new StringBuilder();
          sb.append("(true ");

          String ns = spec.ns();

          if (!"*".equals(ns)) {
            sb.append(AND).append(PgConstants.COLUMN_HEADER).append(CONTAINS_JSONB);
          }

          String type = spec.type();
          if (type != null && !"*".equals(type)) {
            sb.append(AND).append(PgConstants.COLUMN_HEADER).append(CONTAINS_JSONB);
          }

          if (filterByAggregateIds(spec)) {
            sb.append(AND).append(PgConstants.COLUMN_HEADER).append(CONTAINS_JSONB);
          }

          if (filterByAggregateIdProperty(spec)) {
            sb.append(AND).append("(");
            sb.append("(header ->> 'version')::int != ? ").append(OR);
            sb.append("(true ");

            for (Entry<String, UUID> entry : spec.aggIdProperties().entrySet()) {
              String exp = calculateJsonbExpressionFromPropertyPath(entry.getKey());
              sb.append(AND).append("(").append(exp).append(" = ? )");
            }
            sb.append("))");
          }
          Map<String, String> meta = spec.meta();
          meta.forEach(
              (key, value) ->
                  sb.append(AND + "(")
                      .append(PgConstants.COLUMN_HEADER)
                      .append(CONTAINS_JSONB)
                      .append(OR) // single
                      .append(PgConstants.COLUMN_HEADER)
                      .append(CONTAINS_JSONB + ")")); // array

          Map<String, Boolean> metaKeyExists = spec.metaKeyExists();
          metaKeyExists.forEach(
              (key, value) ->
                  sb.append(AND)
                      .append(Boolean.TRUE.equals(value) ? "" : "NOT ")
                      .append("jsonb_path_exists(" + PgConstants.COLUMN_HEADER + ", ?::jsonpath)"));
          sb.append(" )");
          predicates.add(sb.toString());
        });
    String predicatesAsString = String.join(OR, predicates);
    String where = "( " + predicatesAsString + " ) " + AND + PgConstants.COLUMN_SER + ">?";
    if (bounded) {
      where += AND + PgConstants.COLUMN_SER + "<=?";
    }
    return where;
  }

  public String createUnboundedSQL() {
    return createUnboundedSQL(false);
  }

  public String createBoundedSQL() {
    return createUnboundedSQL(true);
  }

  private String createUnboundedSQL(boolean bounded) {

    if (useTemporaryTable()) {
      return "INSERT INTO "
          + tempTableName
          + "("
          + PgConstants.COLUMN_SER
          + ") SELECT "
          + PgConstants.COLUMN_SER
          + FROM
          + PgConstants.TABLE_FACT
          + WHERE
          + createWhereClause(bounded);
      // we don't need the order by here, because it will be ordered when reading from the temp
      // table

    } else
      return "SELECT "
          + (serialsOnly ? PgConstants.COLUMN_SER : PgConstants.PROJECTION_FACT)
          + FROM
          + PgConstants.TABLE_FACT
          + WHERE
          + createWhereClause(bounded)
          + ORDER_BY
          + PgConstants.COLUMN_SER
          + " ASC";
  }

  private boolean useTemporaryTable() {
    return tempTableName != null;
  }

  /**
   * Probes a bounded recent serial range before searching all matches. Both branches share one
   * statement snapshot, and COALESCE skips the fallback when the recent probe succeeds.
   */
  public String createStateSQL(long backwardScanWindow) {
    return createStateSQL(backwardScanWindow, false);
  }

  public String createStateSQL(long backwardScanWindow, boolean bounded) {
    String matchingSerials =
        "SELECT "
            + PgConstants.COLUMN_SER
            + FROM
            + PgConstants.TABLE_FACT
            + WHERE
            + createWhereClause(bounded);
    // sql query is not easy to grasp, but roughly does something like:
    // select COALESCE( <most recent quick lookup> , <most recent full GIN search>)
    //
    // for 90% of the cases, the first is faster, but if we're looking for a very rarely inserted
    // facts, it might have a catastrophic runtime.
    //
    // Therefore we limit the search for "most recent" to the last $backwardScanWindow facts, and
    // fall back to the safer method of using a GIN (probably still tail) to search.
    String sql =
        "WITH boundary AS MATERIALIZED (SELECT MAX(ser)-"
            + backwardScanWindow
            + " AS cutoff FROM fact)"
            + "SELECT COALESCE(("
            // try backwards scan downto cutoff, means: find the most recent matching ser or NULL if
            // none was found within the last 20k facts
            + matchingSerials
            + " AND ser > (SELECT cutoff FROM boundary) ORDER BY ser DESC LIMIT 1),"
            //
            // only if the above returns NULL, we try the GIN index approach instead:
            + " (WITH subq AS MATERIALIZED ("
            + matchingSerials
            + ") SELECT MAX(ser) FROM subq), 0)";
    log.trace("creating state SQL for {} - SQL={}", factSpecs, sql);
    return sql;
  }

  /**
   * @deprecated will be removed with CHUNKED
   * @param tempTableName
   * @return
   */
  @Deprecated(since = "0.11.0", forRemoval = true)
  public PgQueryBuilder useTempTable(@NonNull String tempTableName) {
    this.tempTableName = tempTableName;
    return this;
  }

  public PgQueryBuilder serialsOnly() {
    serialsOnly = true;
    return this;
  }
}
