package org.factcast.example.smilepoc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc")
public record PocProperties(
    Csv csv,
    Postgres postgres,
    boolean reset,
    boolean skipLoad,
    Bench bench) {

  public record Csv(String path) {}

  public record Postgres(String url, String user, String password) {}

  public record Bench(
      int warmup,
      int singleIterations,
      int bulk100Iterations,
      int bulk1000Iterations,
      int parseIterations,
      int filterIterations,
      int invalidationRuns,
      int insertBatchSize,
      String reportCsv) {}
}
