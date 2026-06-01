package org.factcast.example.smilepoc.bench;

import java.util.Arrays;

public final class Stats {

  private final long[] samples;
  private int idx;
  private boolean sorted;

  public Stats(int capacity) {
    this.samples = new long[capacity];
  }

  public void add(long nanos) {
    if (idx < samples.length) {
      samples[idx++] = nanos;
      sorted = false;
    }
  }

  public int size() {
    return idx;
  }

  public long total() {
    long t = 0;
    for (int i = 0; i < idx; i++) t += samples[i];
    return t;
  }

  public long p50() {
    return percentile(0.50);
  }

  public long p95() {
    return percentile(0.95);
  }

  public long p99() {
    return percentile(0.99);
  }

  public long max() {
    if (idx == 0) return 0;
    ensureSorted();
    return samples[idx - 1];
  }

  public long percentile(double p) {
    if (idx == 0) return 0;
    ensureSorted();
    int rank = (int) Math.ceil(p * idx) - 1;
    if (rank < 0) rank = 0;
    if (rank >= idx) rank = idx - 1;
    return samples[rank];
  }

  private void ensureSorted() {
    if (!sorted) {
      Arrays.sort(samples, 0, idx);
      sorted = true;
    }
  }
}
