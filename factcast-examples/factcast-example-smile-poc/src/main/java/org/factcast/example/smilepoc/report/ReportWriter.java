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
package org.factcast.example.smilepoc.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportWriter {

  private final PocProperties props;
  private final List<Measurement> measurements = new ArrayList<>();

  public void add(Measurement m) {
    measurements.add(m);
  }

  public void flushConsole() {
    StringBuilder sb = new StringBuilder();
    sb.append('\n').append("================ PoC results ================").append('\n');
    sb.append(
        String.format(
            "%-12s %-22s %10s %10s %10s %10s %10s %12s  %s%n",
            "variant", "benchmark", "n", "p50", "p95", "p99", "max", "total", "note"));
    for (Measurement m : measurements) {
      sb.append(
          String.format(
              "%-12s %-22s %10d %10s %10s %10s %10s %12s  %s%n",
              m.variant(),
              m.benchmark(),
              m.n(),
              fmt(m.p50Nanos()),
              fmt(m.p95Nanos()),
              fmt(m.p99Nanos()),
              fmt(m.maxNanos()),
              fmtTotal(m.totalNanos()),
              m.note() == null ? "" : m.note()));
    }
    sb.append("=============================================").append('\n');
    log.info(sb.toString());
  }

  public void writeCsv() throws IOException {
    Path out = Path.of(props.bench().reportCsv());
    Files.createDirectories(out.getParent() == null ? Path.of(".") : out.getParent());
    boolean exists = Files.exists(out);
    StringBuilder sb = new StringBuilder();
    if (!exists) {
      sb.append("timestamp,variant,benchmark,n,p50_ns,p95_ns,p99_ns,max_ns,total_ns,note")
          .append('\n');
    }
    String now = Instant.now().toString();
    for (Measurement m : measurements) {
      sb.append(now).append(',');
      sb.append(csv(m.variant())).append(',');
      sb.append(csv(m.benchmark())).append(',');
      sb.append(m.n()).append(',');
      sb.append(m.p50Nanos()).append(',');
      sb.append(m.p95Nanos()).append(',');
      sb.append(m.p99Nanos()).append(',');
      sb.append(m.maxNanos()).append(',');
      sb.append(m.totalNanos()).append(',');
      sb.append(csv(m.note() == null ? "" : m.note())).append('\n');
    }
    Files.writeString(
        out,
        sb.toString(),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
        StandardOpenOption.WRITE);
    log.info("Wrote {} rows to {}", measurements.size(), out);
  }

  private static String csv(String s) {
    if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) {
      return s;
    }
    return '"' + s.replace("\"", "\"\"") + '"';
  }

  private static String fmt(long ns) {
    if (ns == 0) return "-";
    if (ns < 1_000) return ns + "ns";
    if (ns < 1_000_000) return String.format("%.1fus", ns / 1_000.0);
    if (ns < 1_000_000_000) return String.format("%.1fms", ns / 1_000_000.0);
    return String.format("%.2fs", ns / 1_000_000_000.0);
  }

  private static String fmtTotal(long ns) {
    if (ns == 0) return "-";
    if (ns < 1_000_000_000) return String.format("%.0fms", ns / 1_000_000.0);
    return String.format("%.2fs", ns / 1_000_000_000.0);
  }
}
