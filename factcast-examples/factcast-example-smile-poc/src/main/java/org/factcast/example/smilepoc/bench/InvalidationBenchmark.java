/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.example.smilepoc.bench;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Times invalidation by (ns, type) for both variants. Runs each DELETE inside an explicit
 * transaction that is rolled back, so the data survives across iterations and across reruns.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvalidationBenchmark {

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final PocProperties props;
  private final ReportWriter report;

  public void run() throws Exception {
    NsType target = pickRepresentativePair();
    if (target == null) {
      log.warn("No data — skipping invalidation benchmark");
      return;
    }
    log.info(
        "Invalidation target: ns={} type={} (rows={})", target.ns, target.type, target.rowCount);

    int runs = props.bench().invalidationRuns();
    Stats jsonbStats = new Stats(runs);
    Stats smileStats = new Stats(runs);

    for (int i = 0; i < runs; i++) {
      jsonbStats.add(
          timeRolledBackDelete(
              "DELETE FROM transformationcache_jsonb WHERE header->>'ns' = ? AND header->>'type' = ?",
              target));
      smileStats.add(
          timeRolledBackDelete(
              "DELETE FROM transformationcache_smile WHERE ns = ? AND type = ?", target));
    }

    String note = String.format("ns=%s type=%s rows=%d", target.ns, target.type, target.rowCount);
    record(jsonbStats, "jsonb", note);
    record(smileStats, "smile", note);
  }

  private long timeRolledBackDelete(String sql, NsType target) throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, target.ns);
        ps.setString(2, target.type);
        long t0 = System.nanoTime();
        ps.executeUpdate();
        long elapsed = System.nanoTime() - t0;
        conn.rollback();
        return elapsed;
      } finally {
        conn.setAutoCommit(true);
      }
    }
  }

  private NsType pickRepresentativePair() {
    return jdbc.query(
        "SELECT ns, type, count(*) AS c FROM transformationcache_smile "
            + "GROUP BY ns, type ORDER BY c DESC LIMIT 1",
        rs -> {
          if (!rs.next()) return null;
          return new NsType(rs.getString("ns"), rs.getString("type"), rs.getLong("c"));
        });
  }

  private void record(Stats s, String variant, String note) {
    Measurement m =
        new Measurement(
            variant,
            "invalidate_ns_type",
            s.size(),
            s.p50(),
            s.p95(),
            s.p99(),
            s.max(),
            s.total(),
            note);
    report.add(m);
    log.info(
        "[{}] invalidate_ns_type runs={} p50={}ms p95={}ms",
        variant,
        s.size(),
        s.p50() / 1_000_000,
        s.p95() / 1_000_000);
  }

  private record NsType(String ns, String type, long rowCount) {}
}
