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
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BulkReadBenchmark {

  private final DataSource dataSource;
  private final KeySampler keys;
  private final PocProperties props;
  private final ReportWriter report;

  public void run() throws Exception {
    runFor(100, props.bench().bulk100Iterations());
    runFor(1000, props.bench().bulk1000Iterations());
  }

  private void runFor(int batchSize, int iterations) throws Exception {
    int needed = batchSize * iterations + props.bench().warmup();
    List<KeySample> sample = keys.sample(needed);
    if (sample.size() < batchSize) {
      log.warn("Not enough keys for bulk size {} — skipping", batchSize);
      return;
    }
    int warmupBatches = Math.max(1, iterations / 10);

    log.info(
        "Bulk read: batchSize={} measuredBatches={} warmupBatches={}",
        batchSize,
        iterations,
        warmupBatches);

    Stats jsonbStats = new Stats(iterations);
    Stats smileStats = new Stats(iterations);

    int idx = 0;
    for (int i = 0; i < iterations + warmupBatches; i++) {
      List<KeySample> slice = sliceCircular(sample, idx, batchSize);
      idx += batchSize;

      long jsonbDt = bulkJsonb(slice);
      long smileDt = bulkSmile(slice);

      if (i >= warmupBatches) {
        jsonbStats.add(jsonbDt);
        smileStats.add(smileDt);
      }
    }

    record(jsonbStats, "jsonb", "bulk_read_" + batchSize, batchSize);
    record(smileStats, "smile", "bulk_read_" + batchSize, batchSize);
  }

  private long bulkJsonb(List<KeySample> slice) {
    String[] keys = slice.stream().map(KeySample::cacheKey).toArray(String[]::new);
    long t0 = System.nanoTime();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT cache_key, header, payload FROM transformationcache_jsonb "
                    + "WHERE cache_key = ANY(?)")) {
      ps.setArray(1, conn.createArrayOf("varchar", keys));
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          rs.getString(1);
          rs.getString(2);
          rs.getString(3);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return System.nanoTime() - t0;
  }

  private long bulkSmile(List<KeySample> slice) {
    String[] keys = slice.stream().map(KeySample::cacheKey).toArray(String[]::new);
    long t0 = System.nanoTime();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps =
            conn.prepareStatement(
                "SELECT cache_key, data FROM transformationcache_smile "
                    + "WHERE cache_key = ANY(?)")) {
      ps.setArray(1, conn.createArrayOf("varchar", keys));
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          rs.getString(1);
          rs.getBytes(2);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return System.nanoTime() - t0;
  }

  private static List<KeySample> sliceCircular(List<KeySample> all, int from, int size) {
    int s = from % all.size();
    int e = s + size;
    if (e <= all.size()) return all.subList(s, e);
    // wrap
    java.util.List<KeySample> out = new java.util.ArrayList<>(size);
    out.addAll(all.subList(s, all.size()));
    out.addAll(all.subList(0, e - all.size()));
    return out;
  }

  private void record(Stats s, String variant, String name, int batchSize) {
    String note = "batch=" + batchSize;
    Measurement m =
        new Measurement(
            variant, name, s.size(), s.p50(), s.p95(), s.p99(), s.max(), s.total(), note);
    report.add(m);
    log.info(
        "[{}] {} n={} p50={}us p95={}us p99={}us",
        variant,
        name,
        s.size(),
        s.p50() / 1000,
        s.p95() / 1000,
        s.p99() / 1000);
  }
}
