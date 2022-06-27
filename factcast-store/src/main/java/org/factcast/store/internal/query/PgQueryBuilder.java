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
import java.util.Map.*;
import java.util.concurrent.atomic.*;
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
        count = setAggId(p, count, spec);
        count = setMeta(p, count, spec);
      }
      p.setLong(++count, serial.get());

      if (statementHolder != null) statementHolder.statement(p);
    };
  }

  private int setMeta(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    Map<String, String> meta = spec.meta();
    for (Entry<String, String> e : meta.entrySet()) {
      p.setString(++count, "{\"meta\":{\"" + e.getKey() + "\":\"" + e.getValue() + "\"}}");
    }
    return count;
  }

  private int setAggId(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    UUID agg = spec.aggId();
    if (agg != null) {
      p.setString(++count, "{\"aggIds\": [\"" + agg + "\"]}");
    }
    return count;
  }

  private int setType(PreparedStatement p, int count, FactSpec spec) throws SQLException {
    String type = spec.type();
    if (type != null) {
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
          if (type != null) {
            sb.append(" AND ").append(PgConstants.COLUMN_HEADER).append(" @> ?::jsonb");
          }

          UUID agg = spec.aggId();
          if (agg != null) {
            sb.append(" AND ").append(PgConstants.COLUMN_HEADER).append(" @> ?::jsonb");
          }
          Map<String, String> meta = spec.meta();
          meta.forEach(
              (key, value) ->
                  sb.append(" AND ").append(PgConstants.COLUMN_HEADER).append(" @> ?::jsonb"));

          sb.append(")");
          predicates.add(sb.toString());
        });
    String predicatesAsString = String.join(" OR ", predicates);
    return "( " + predicatesAsString + " ) AND " + PgConstants.COLUMN_SER + ">?";
  }

  public String createSQL() {
    String sql =
        "SELECT "
            + PgConstants.PROJECTION_FACT
            + " FROM "
            + PgConstants.TABLE_FACT
            + " WHERE "
            + createWhereClause()
            + " ORDER BY "
            + PgConstants.COLUMN_SER
            + " ASC";
    log.trace("{} createSQL={}", factSpecs, sql);
    return sql;
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
    log.trace("{} createStateSQL={}", factSpecs, sql);
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
    log.trace("{} catchupSQL={}", factSpecs, sql);
    return sql;
  }
}
