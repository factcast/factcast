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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * End-to-end aggId-property-style filter: random key → DB fetch → parse → walk → compare. Mirrors
 * the work done by {@code AggIdPropertyMatcher} in factcast-store, but local to the PoC.
 */
@Slf4j
@Component
public class AggIdFilterBenchmark {

  private static final java.util.regex.Pattern UUID_PATTERN =
      java.util.regex.Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final JdbcTemplate jdbc;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;
  private final PocProperties props;
  private final KeySampler keys;
  private final ReportWriter report;

  public AggIdFilterBenchmark(
      JdbcTemplate jdbc,
      ObjectMapper jsonMapper,
      @Qualifier("smileMapper") ObjectMapper smileMapper,
      PocProperties props,
      KeySampler keys,
      ReportWriter report) {
    this.jdbc = jdbc;
    this.jsonMapper = jsonMapper;
    this.smileMapper = smileMapper;
    this.props = props;
    this.keys = keys;
    this.report = report;
  }

  public void run() throws Exception {
    Map<String, String> fixture = pickFixture();
    if (fixture.isEmpty()) {
      log.warn("Could not derive aggId fixture (no UUID-shaped top-level fields in any payload)");
      return;
    }
    log.info("AggId fixture: {}", fixture);

    int n = props.bench().filterIterations();
    List<KeySample> sample = keys.sample(n + props.bench().warmup());
    int actual = Math.min(n + props.bench().warmup(), sample.size());
    int warmup = Math.max(1, actual / 10);
    int measured = actual - warmup;

    Stats jsonbStats = new Stats(measured);
    Stats smileStats = new Stats(measured);
    int jsonbMatches = 0;
    int smileMatches = 0;

    for (int i = 0; i < actual; i++) {
      KeySample k = sample.get(i);

      long t0 = System.nanoTime();
      boolean jsonbMatch = matchJsonb(k, fixture);
      long jsonbDt = System.nanoTime() - t0;

      long t1 = System.nanoTime();
      boolean smileMatch = matchSmile(k, fixture);
      long smileDt = System.nanoTime() - t1;

      if (i >= warmup) {
        jsonbStats.add(jsonbDt);
        smileStats.add(smileDt);
        if (jsonbMatch) jsonbMatches++;
        if (smileMatch) smileMatches++;
      }
    }

    record(jsonbStats, "jsonb", jsonbMatches);
    record(smileStats, "smile", smileMatches);
  }

  private Map<String, String> pickFixture() throws Exception {
    String payload =
        jdbc.queryForObject(
            "SELECT payload::text FROM transformationcache_jsonb LIMIT 1", String.class);
    if (payload == null) return Map.of();
    JsonNode root = jsonMapper.readTree(payload);
    Iterator<Map.Entry<String, JsonNode>> it = root.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> e = it.next();
      JsonNode v = e.getValue();
      if (v.isTextual() && UUID_PATTERN.matcher(v.asText()).matches()) {
        return Map.of(e.getKey(), v.asText());
      }
    }
    return Map.of();
  }

  private boolean matchJsonb(KeySample k, Map<String, String> fixture) {
    List<String> rows =
        jdbc.queryForList(
            "SELECT payload::text FROM transformationcache_jsonb WHERE cache_key = ?",
            String.class,
            k.cacheKey());
    if (rows.isEmpty()) return false;
    try {
      JsonNode payload = jsonMapper.readTree(rows.get(0));
      return matches(payload, fixture);
    } catch (Exception e) {
      return false;
    }
  }

  private boolean matchSmile(KeySample k, Map<String, String> fixture) {
    List<byte[]> rows =
        jdbc.query(
            "SELECT data FROM transformationcache_smile WHERE cache_key = ?",
            (rs, i) -> rs.getBytes(1),
            k.cacheKey());
    if (rows.isEmpty()) return false;
    try {
      JsonNode root = smileMapper.readTree(rows.get(0));
      JsonNode payload = root.path("p");
      if (payload.isMissingNode()) return false;
      return matches(payload, fixture);
    } catch (Exception e) {
      return false;
    }
  }

  /** Mirrors AggIdPropertyMatcher.aggIdPropertiesMatch — walks dotted path, compares as text. */
  private static boolean matches(JsonNode payloadRoot, Map<String, String> aggIdProperties) {
    for (Map.Entry<String, String> e : aggIdProperties.entrySet()) {
      JsonNode n = payloadRoot;
      for (String seg : e.getKey().split("\\.")) {
        n = n.path(seg);
        if (n.isMissingNode()) return false;
      }
      if (!e.getValue().equals(n.asText())) return false;
    }
    return true;
  }

  private void record(Stats s, String variant, int matches) {
    String note = String.format("matches=%d", matches);
    Measurement m =
        new Measurement(
            variant, "aggid_filter", s.size(), s.p50(), s.p95(), s.p99(), s.max(), s.total(), note);
    report.add(m);
    log.info(
        "[{}] aggid_filter n={} matches={} p50={}us p95={}us total={}ms",
        variant,
        s.size(),
        matches,
        s.p50() / 1000,
        s.p95() / 1000,
        s.total() / 1_000_000);
  }
}
