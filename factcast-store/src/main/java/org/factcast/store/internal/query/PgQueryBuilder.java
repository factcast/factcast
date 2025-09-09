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

  private final @NonNull List<FactSpec> factSpecs;
  private final CurrentStatementHolder statementHolder;

  public PgQueryBuilder(@NonNull List<FactSpec> specs) {
    factSpecs = specs;
    statementHolder = null;
  }

  public PgQueryBuilder(@NonNull List<FactSpec> specs, @NonNull CurrentStatementHolder holder) {
    factSpecs = specs;
    this.statementHolder = holder;
  }

  public PreparedStatementSetter createStatementSetter(@NonNull AtomicLong serial) {
    return p -> {
      // TODO vulnerable of json injection attack
      int count = 0;
      for (FactSpec spec : factSpecs) {
        count = setNs(p, count, spec);
        count = setType(p, count, spec);
        // version is intentionally not used here
        count = setAggIds(p, count, spec);
        count = setMeta(p, count, spec);
        count = setMetaKeyExists(p, count, spec);
      }
      p.setLong(++count, serial.get());

      if (statementHolder != null) {
        statementHolder.statement(p);
      }
    };
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
          spec.mergedAggIds().stream()
              .map(UUID::toString)
              .collect(Collectors.joining("\",\"", "\"", "\""));
      p.setString(++count, "{\"aggIds\": [" + a + "]}");
    }
    return count;
  }

  private static boolean filterByAggregateIds(FactSpec specs) {
    Set<UUID> aggIds = specs.mergedAggIds();
    return aggIds != null && !aggIds.isEmpty();
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

  private String createWhereClause() {
    List<String> predicates = new LinkedList<>();
    factSpecs.forEach(
        spec -> {
          StringBuilder sb = new StringBuilder();
          sb.append("(1=1");

          String ns = spec.ns();
          if (ns != null && !"*".equals(ns)) {
            sb.append(" AND ").append(PgConstants.COLUMN_HEADER).append(" @> ?::jsonb");
          }

          String type = spec.type();
          if (type != null && !"*".equals(type)) {
            sb.append(" AND ").append(PgConstants.COLUMN_HEADER).append(" @> ?::jsonb");
          }

          if (filterByAggregateIds(spec)) {
            sb.append(" AND ").append(PgConstants.COLUMN_HEADER).append(" @> ?::jsonb");
          }

          Map<String, String> meta = spec.meta();
          meta.forEach(
              (key, value) ->
                  sb.append(" AND (")
                      .append(PgConstants.COLUMN_HEADER)
                      .append(" @> ?::jsonb OR ") // single
                      .append(PgConstants.COLUMN_HEADER)
                      .append(" @> ?::jsonb)")); // array

          Map<String, Boolean> metaKeyExists = spec.metaKeyExists();
          metaKeyExists.forEach(
              (key, value) ->
                  sb.append(" AND ")
                      .append(Boolean.TRUE.equals(value) ? "" : "NOT ")
                      .append("jsonb_path_exists(" + PgConstants.COLUMN_HEADER + ", ?::jsonpath)"));
          sb.append(")");
          predicates.add(sb.toString());
        });
    String predicatesAsString = String.join(" OR ", predicates);
    return "( " + predicatesAsString + " ) AND " + PgConstants.COLUMN_SER + ">?";
  }

  public String createSQL() {
    return "SELECT "
        + PgConstants.PROJECTION_FACT
        + " FROM "
        + PgConstants.TABLE_FACT
        + " WHERE "
        + createWhereClause()
        + " ORDER BY "
        + PgConstants.COLUMN_SER
        + " ASC";
  }

  public String createStateSQL() {
    String sql =
        "SELECT "
            + PgConstants.COLUMN_SER
            + " FROM "
            + PgConstants.TABLE_FACT
            + " WHERE "
            + createWhereClause()
            + " ORDER BY "
            + PgConstants.COLUMN_SER
            + " DESC LIMIT 1";
    log.trace("creating state SQL for {} - SQL={}", factSpecs, sql);
    return sql;
  }

  public String catchupSQL() {
    String sql = //
        "INSERT INTO "
            + PgConstants.TABLE_CATCHUP
            + " ("
            + PgConstants.COLUMN_SER
            + ") "
            + "(SELECT "
            + PgConstants.COLUMN_SER
            + " FROM "
            + //
            PgConstants.TABLE_FACT
            + " WHERE ("
            + createWhereClause()
            + //
            "))";
    log.trace("creating catchup-table SQL for {} - SQL={}", factSpecs, sql);
    return sql;
  }
}
