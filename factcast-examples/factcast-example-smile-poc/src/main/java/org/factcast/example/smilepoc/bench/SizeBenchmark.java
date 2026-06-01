package org.factcast.example.smilepoc.bench;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SizeBenchmark {

  private final JdbcTemplate jdbc;
  private final ReportWriter report;

  public void run() {
    measure("jsonb", "transformationcache_jsonb");
    measure("smile", "transformationcache_smile");
  }

  private void measure(String variant, String table) {
    long heap = sizeBytes("pg_relation_size(?::regclass)", table);
    long indexes = sizeBytes("pg_indexes_size(?::regclass)", table);
    long toast =
        sizeBytes(
            "COALESCE(pg_total_relation_size((SELECT reltoastrelid FROM pg_class WHERE oid = ?::regclass)), 0)",
            table);
    long total = sizeBytes("pg_total_relation_size(?::regclass)", table);
    long rows = countRows(table);

    String note =
        String.format(
            "rows=%d heap=%s indexes=%s toast=%s total=%s",
            rows, human(heap), human(indexes), human(toast), human(total));
    log.info("[{}] {}", variant, note);
    report.add(Measurement.single(variant, "size", total, rows, note));
  }

  private long sizeBytes(String fn, String table) {
    Long n = jdbc.queryForObject("SELECT " + fn, Long.class, table);
    return n == null ? 0 : n;
  }

  private long countRows(String table) {
    Long n = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    return n == null ? 0 : n;
  }

  private static String human(long b) {
    if (b < 1024) return b + "B";
    if (b < 1024 * 1024) return String.format("%.1fKB", b / 1024.0);
    if (b < 1024L * 1024 * 1024) return String.format("%.1fMB", b / (1024.0 * 1024));
    return String.format("%.2fGB", b / (1024.0 * 1024 * 1024));
  }
}
