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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * Pre-fetches a fixed set of rows into memory, then times only the Java-side parse step: text JSON
 * String → JsonNode (variant A) vs SMILE byte[] → JsonNode (variant B).
 */
@Slf4j
@Component
public class ParseBenchmark {

  private final JdbcTemplate jdbc;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;
  private final PocProperties props;
  private final ReportWriter report;

  public ParseBenchmark(
      JdbcTemplate jdbc,
      ObjectMapper jsonMapper,
      @Qualifier("smileMapper") ObjectMapper smileMapper,
      PocProperties props,
      ReportWriter report) {
    this.jdbc = jdbc;
    this.jsonMapper = jsonMapper;
    this.smileMapper = smileMapper;
    this.props = props;
    this.report = report;
  }

  public void run() throws Exception {
    int n = props.bench().parseIterations();
    log.info("Pre-fetching {} rows for parse benchmark", n);

    List<String> jsonbHeaders = new ArrayList<>(n);
    List<String> jsonbPayloads = new ArrayList<>(n);
    jdbc.query(
        "SELECT header::text, payload::text FROM transformationcache_jsonb LIMIT ?",
        (RowCallbackHandler)
            rs -> {
              jsonbHeaders.add(rs.getString(1));
              jsonbPayloads.add(rs.getString(2));
            },
        n);

    List<byte[]> smileRows = new ArrayList<>(n);
    jdbc.query(
        "SELECT data FROM transformationcache_smile LIMIT ?",
        (RowCallbackHandler) rs -> smileRows.add(rs.getBytes(1)),
        n);

    int actual = Math.min(jsonbHeaders.size(), smileRows.size());
    int warmup = Math.max(1, actual / 10);
    int measured = actual - warmup;
    log.info("Parse: warmup={} measured={}", warmup, measured);

    Stats jsonbStats = new Stats(measured);
    Stats smileStats = new Stats(measured);

    for (int i = 0; i < actual; i++) {
      long t0 = System.nanoTime();
      jsonMapper.readTree(jsonbHeaders.get(i));
      jsonMapper.readTree(jsonbPayloads.get(i));
      long jsonbDt = System.nanoTime() - t0;

      long t1 = System.nanoTime();
      smileMapper.readTree(smileRows.get(i));
      long smileDt = System.nanoTime() - t1;

      if (i >= warmup) {
        jsonbStats.add(jsonbDt);
        smileStats.add(smileDt);
      }
    }

    record(jsonbStats, "jsonb", "parse", "text→JsonNode (header+payload)");
    record(smileStats, "smile", "parse", "smile bytes→JsonNode (combined)");
  }

  private void record(Stats s, String variant, String name, String note) {
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
