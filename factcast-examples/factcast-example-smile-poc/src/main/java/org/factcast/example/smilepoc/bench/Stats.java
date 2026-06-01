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
