package org.factcast.example.smilepoc.report;

public record Measurement(
    String variant,
    String benchmark,
    long n,
    long p50Nanos,
    long p95Nanos,
    long p99Nanos,
    long maxNanos,
    long totalNanos,
    String note) {

  public static Measurement single(
      String variant, String benchmark, long totalNanos, long n, String note) {
    return new Measurement(variant, benchmark, n, 0, 0, 0, 0, totalNanos, note);
  }
}
