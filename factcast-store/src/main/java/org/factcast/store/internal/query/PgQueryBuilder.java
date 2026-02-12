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
import java.util.stream.Collectors;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.FactSpec;
import org.factcast.store.internal.PgConstants;
import org.jetbrains.annotations.NotNull;
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

  private final @NonNull List<FactSpec> factSpecs;
  private final CurrentStatementHolder statementHolder;
  private String tempTableName = null;

  public PgQueryBuilder(@NonNull List<FactSpec> specs) {
    factSpecs = specs;
    statementHolder = null;
  }

  public PgQueryBuilder(@NonNull List<FactSpec> specs, @NonNull CurrentStatementHolder holder) {
    factSpecs = specs;
    this.statementHolder = holder;
  }

  public PreparedStatementSetter createStatementSetter() {
    return p -> {
      int count = 0;
      for (FactSpec spec : factSpecs) {
        count = setNs(p, count, spec);
        count = setType(p, count, spec);
        // version is intentionally not used here
        count = setAggIds(p, count, spec);
        count = setAggProperties(p, count, spec);
        count = setMeta(p, count, spec);
        count = setMetaKeyExists(p, count, spec);
      }

      if (statementHolder != null) {
        statementHolder.statement(p);
      }
    };
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
    if (ns != null && !"*".equals(ns)) {
      p.setString(++count, "{\"ns\": \"" + spec.ns() + "\"}");
    }
    return count;
  }

  @SuppressWarnings("java:S3776")
  private String createWhereClause() {
    List<String> predicates = new LinkedList<>();
    factSpecs.forEach(
        spec -> {
          StringBuilder sb = new StringBuilder();
          sb.append("(true ");

          String ns = spec.ns();

          if (ns != null && !"*".equals(ns)) {
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
            sb.append("(header ->> 'version')::int != ? " + OR);
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
                      .append(CONTAINS_JSONB + OR) // single
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

    // issue4328
    // we don't want to parametrize the serial, so that PG is forced to recalculate the plan

    return "( " + predicatesAsString + " ) ";
  }

  public String createSQL(long serial) {

    if (tempTableName != null) {
      return "INSERT INTO "
          + tempTableName
          + "("
          + PgConstants.COLUMN_SER
          + ") SELECT "
          + PgConstants.COLUMN_SER
          + FROM
          + PgConstants.TABLE_FACT
          + WHERE
          + createWhereClause()
          + AND
          + createSerialCriterionFor(serial);
      // we don't need the order by here, because it will be ordered when reading

    } else
      return "SELECT "
          + PgConstants.PROJECTION_FACT
          + FROM
          + PgConstants.TABLE_FACT
          + WHERE
          + createWhereClause()
          + AND
          + createSerialCriterionFor(serial)
          + ORDER_BY
          + PgConstants.COLUMN_SER
          + " ASC";
  }

  public String createStateSQL(long serial) {

    String sql =
        "SELECT "
            + PgConstants.COLUMN_SER
            + FROM
            + PgConstants.TABLE_FACT
            + WHERE
            + createWhereClause()
            + AND
            + createSerialCriterionFor(serial)
            + ORDER_BY
            + PgConstants.COLUMN_SER
            + " DESC LIMIT 1";
    log.trace("creating state SQL for {} - SQL={}", factSpecs, sql);
    return sql;
  }

  @NotNull
  // issue4328
  private static String createSerialCriterionFor(long serial) {
    return PgConstants.COLUMN_SER + " > " + serial;
  }

  public void moveSerialsToTempTable(@NonNull String tempTableName) {
    this.tempTableName = tempTableName;
  }
}
