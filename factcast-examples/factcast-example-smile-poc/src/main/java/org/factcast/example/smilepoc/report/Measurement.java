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
