package org.factcast.example.smilepoc.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.factcast.example.smilepoc.PocProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmileLoader {

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;
  private final PocProperties props;

  public SmileLoader(
      DataSource dataSource,
      JdbcTemplate jdbc,
      ObjectMapper jsonMapper,
      @Qualifier("smileMapper") ObjectMapper smileMapper,
      PocProperties props) {
    this.dataSource = dataSource;
    this.jdbc = jdbc;
    this.jsonMapper = jsonMapper;
    this.smileMapper = smileMapper;
    this.props = props;
  }

  public Result load(Path csv) throws Exception {
    int batchSize = props.bench().insertBatchSize();
    log.info("Batch INSERT into transformationcache_smile from {} (batch={})", csv, batchSize);
    long rejected = 0;
    long inserted = 0;
    long t0 = System.nanoTime();

    String sql =
        "INSERT INTO transformationcache_smile "
            + "(cache_key, fact_id, ns, type, data) VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT DO NOTHING";

    try (Connection conn = dataSource.getConnection();
        BufferedReader reader = new BufferedReader(new FileReader(csv.toFile()));
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader)) {
      conn.setAutoCommit(false);
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        int inBatch = 0;
        for (CSVRecord rec : parser) {
          try {
            String cacheKey = rec.get("cache_key");
            String headerJson = rec.get("header");
            String payloadJson = rec.get("payload");
            CacheKeyParser.Parsed key = CacheKeyParser.parse(cacheKey);

            JsonNode header = jsonMapper.readTree(headerJson);
            JsonNode payload = jsonMapper.readTree(payloadJson);

            String ns = textOrNull(header, "ns");
            String type = textOrNull(header, "type");
            if (ns == null || type == null) {
              rejected++;
              continue;
            }

            ObjectNode envelope = smileMapper.createObjectNode();
            envelope.set("h", header);
            envelope.set("p", payload);
            byte[] smile = smileMapper.writeValueAsBytes(envelope);

            ps.setString(1, cacheKey);
            ps.setObject(2, key.factId(), Types.OTHER);
            ps.setString(3, ns);
            ps.setString(4, type);
            ps.setBytes(5, smile);
            ps.addBatch();
            inBatch++;
            inserted++;

            if (inBatch >= batchSize) {
              ps.executeBatch();
              conn.commit();
              inBatch = 0;
            }
          } catch (Exception e) {
            rejected++;
            if (rejected <= 5) {
              log.warn("rejected row {}: {}", rec.getRecordNumber(), e.getMessage());
            }
          }
        }
        if (inBatch > 0) {
          ps.executeBatch();
          conn.commit();
        }
      }
    }

    long elapsed = System.nanoTime() - t0;
    log.info(
        "Batch INSERT loaded {} rows in {} ms ({} rejected)",
        inserted,
        elapsed / 1_000_000,
        rejected);
    return new Result(inserted, elapsed, rejected);
  }

  public long count() {
    Long n = jdbc.queryForObject("SELECT count(*) FROM transformationcache_smile", Long.class);
    return n == null ? 0 : n;
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  public record Result(long rows, long elapsedNanos, long rejected) {}
}
