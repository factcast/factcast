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
package org.factcast.example.smilepoc;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "poc")
public record PocProperties(
    Csv csv, Postgres postgres, boolean reset, boolean skipLoad, Bench bench) {

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
      int taxIterations,
      int wireSizeSample,
      String reportCsv) {}
}
