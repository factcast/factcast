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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.FactCastJson;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.loader.SampleLoader;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Experiment 1 — the "re-serialization tax".
 *
 * <p>Today a transformed fact is stored as JSON text and put on the gRPC wire verbatim ({@code
 * MSG_Fact} carries {@code string header} / {@code string payload}) — effectively free. If the
 * transformationcache instead stored SMILE (EI-1241) but the wire stayed string, the server would
 * have to reconstruct the JSON string from SMILE on every cache hit: {@code smile bytes → JsonNode
 * → String}. This bench measures that added CPU per fact (header + payload), using the production
 * JSON mapper ({@link FactCastJson}) for the string side.
 */
@Slf4j
@Component
public class ReserializationTaxBenchmark {

  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;
  private final SampleLoader samples;
  private final PocProperties props;
  private final ReportWriter report;

  public ReserializationTaxBenchmark(
      ObjectMapper jsonMapper,
      @Qualifier("smileMapper") ObjectMapper smileMapper,
      SampleLoader samples,
      PocProperties props,
      ReportWriter report) {
    this.jsonMapper = jsonMapper;
    this.smileMapper = smileMapper;
    this.samples = samples;
    this.props = props;
    this.report = report;
  }

  public void run() throws Exception {
    int iterations = props.bench().taxIterations();
    int sampleSize = Math.max(1, props.bench().wireSizeSample());
    int warmup = Math.min(props.bench().warmup(), Math.max(0, iterations - 1));

    List<SampleLoader.Sample> data = samples.load(sampleSize);
    int n = data.size();
    log.info(
        "Re-serialization tax: {} iterations over {} samples (warmup={})", iterations, n, warmup);

    // model the cache holding SMILE: pre-encode header and payload to SMILE bytes once.
    byte[][] smileHeaders = new byte[n][];
    byte[][] smilePayloads = new byte[n][];
    String[] jsonHeaders = new String[n];
    String[] jsonPayloads = new String[n];
    for (int i = 0; i < n; i++) {
      SampleLoader.Sample s = data.get(i);
      jsonHeaders[i] = s.header();
      jsonPayloads[i] = s.payload();
      smileHeaders[i] = smileMapper.writeValueAsBytes(jsonMapper.readTree(s.header()));
      smilePayloads[i] = smileMapper.writeValueAsBytes(jsonMapper.readTree(s.payload()));
    }

    int measured = Math.max(0, iterations - warmup);
    Stats todayStats = new Stats(measured);
    Stats taxStats = new Stats(measured);
    long sink = 0;

    for (int it = 0; it < iterations; it++) {
      int i = it % n;

      // today: cache already holds the string -> wire string is a passthrough.
      long t0 = System.nanoTime();
      String wireH = jsonHeaders[i];
      String wireP = jsonPayloads[i];
      sink += wireH.length() + wireP.length();
      long todayDt = System.nanoTime() - t0;

      // smile-cache + string-wire: must rebuild the JSON string from SMILE.
      long t1 = System.nanoTime();
      JsonNode h = smileMapper.readTree(smileHeaders[i]);
      String hs = FactCastJson.writeValueAsString(h);
      JsonNode p = smileMapper.readTree(smilePayloads[i]);
      String ps = FactCastJson.writeValueAsString(p);
      sink += hs.length() + ps.length();
      long taxDt = System.nanoTime() - t1;

      if (it >= warmup) {
        todayStats.add(todayDt);
        taxStats.add(taxDt);
      }
    }

    record(
        todayStats,
        "today(str→str)",
        "reserialize_tax",
        "wire string = stored string (passthrough)");
    record(taxStats, "smile→str", "reserialize_tax", "smile bytes→JsonNode→String (added cost)");

    double taxUsP50 = taxStats.p50() / 1000.0;
    double factsPerSec = taxStats.size() / (taxStats.total() / 1_000_000_000.0);
    log.info(
        "Re-serialization tax: +{}us/fact (p50), throughput {} facts/s on the smile→string path. "
            + "At 10k facts/s that is ~{}ms CPU/s ({}% of one core). sink={}",
        String.format("%.2f", taxUsP50),
        String.format("%.0f", factsPerSec),
        String.format("%.1f", taxStats.p50() / 1000.0 * 10_000 / 1000.0),
        String.format("%.1f", taxStats.p50() / 1_000_000_000.0 * 10_000 * 100),
        sink);
  }

  private void record(Stats s, String variant, String name, String note) {
    report.add(
        new Measurement(
            variant, name, s.size(), s.p50(), s.p95(), s.p99(), s.max(), s.total(), note));
    log.info(
        "[{}] {} n={} p50={}us p95={}us", variant, name, s.size(), s.p50() / 1000, s.p95() / 1000);
  }
}
