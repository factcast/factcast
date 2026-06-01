package org.factcast.example.smilepoc.loader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.sql.Connection;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonbLoader {

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;

  /**
   * Streams the CSV directly to Postgres via COPY. JSONB columns accept text JSON, so the file
   * goes through unchanged. Returns elapsed nanoseconds and rows loaded.
   */
  public Result load(Path csv) throws Exception {
    log.info("COPY into transformationcache_jsonb from {}", csv);
    long t0 = System.nanoTime();
    long copied;
    try (Connection conn = dataSource.getConnection();
        Reader reader = new BufferedReader(new FileReader(csv.toFile()))) {
      CopyManager copy = conn.unwrap(PGConnection.class).getCopyAPI();
      copied =
          copy.copyIn(
              "COPY transformationcache_jsonb (cache_key, header, payload) "
                  + "FROM STDIN WITH (FORMAT csv, HEADER true)",
              reader);
    } catch (IOException e) {
      throw new RuntimeException("COPY failed", e);
    }
    long elapsed = System.nanoTime() - t0;
    log.info("COPY loaded {} rows in {} ms", copied, elapsed / 1_000_000);
    return new Result(copied, elapsed);
  }

  public long count() {
    Long n = jdbc.queryForObject("SELECT count(*) FROM transformationcache_jsonb", Long.class);
    return n == null ? 0 : n;
  }

  public record Result(long rows, long elapsedNanos) {}
}
