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

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SingleReadBenchmark {

  private final JdbcTemplate jdbc;
  private final KeySampler keys;
  private final PocProperties props;
  private final ReportWriter report;

  public void run() {
    int total = props.bench().warmup() + props.bench().singleIterations();
    List<KeySample> sample = keys.sample(total);

    if (sample.size() < total) {
      log.warn("Only {} keys available; reducing iteration count from {}", sample.size(), total);
      total = sample.size();
    }
    int warmup = Math.min(props.bench().warmup(), total / 10);
    int measured = total - warmup;

    log.info("Single read: warmup={} measured={}", warmup, measured);

    Stats jsonbStats = new Stats(measured);
    Stats smileStats = new Stats(measured);

    for (int i = 0; i < total; i++) {
      KeySample k = sample.get(i);
      long t0 = System.nanoTime();
      jdbc.queryForList(
          "SELECT header, payload FROM transformationcache_jsonb WHERE cache_key = ?",
          k.cacheKey());
      long jsonbDt = System.nanoTime() - t0;

      long t1 = System.nanoTime();
      jdbc.queryForList(
          "SELECT data FROM transformationcache_smile WHERE cache_key = ?", k.cacheKey());
      long smileDt = System.nanoTime() - t1;

      if (i >= warmup) {
        jsonbStats.add(jsonbDt);
        smileStats.add(smileDt);
      }
    }

    record(jsonbStats, "jsonb", "single_read");
    record(smileStats, "smile", "single_read");
  }

  private void record(Stats s, String variant, String name) {
    Measurement m =
        new Measurement(
            variant, name, s.size(), s.p50(), s.p95(), s.p99(), s.max(), s.total(), null);
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
