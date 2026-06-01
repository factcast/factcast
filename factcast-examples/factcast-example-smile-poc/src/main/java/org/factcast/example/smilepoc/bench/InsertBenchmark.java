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

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.loader.JsonbLoader;
import org.factcast.example.smilepoc.loader.SmileLoader;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InsertBenchmark {

  private final PocProperties props;
  private final JsonbLoader jsonbLoader;
  private final SmileLoader smileLoader;
  private final ReportWriter report;

  public void run() throws Exception {
    Path csv = Path.of(props.csv().path());
    log.info("Insert benchmark using {}", csv);

    JsonbLoader.Result jsonb = jsonbLoader.load(csv);
    report.add(
        Measurement.single(
            "jsonb",
            "insert",
            jsonb.elapsedNanos(),
            jsonb.rows(),
            String.format(
                "COPY  rows/s=%.0f", jsonb.rows() / (jsonb.elapsedNanos() / 1_000_000_000.0))));

    SmileLoader.Result smile = smileLoader.load(csv);
    report.add(
        Measurement.single(
            "smile",
            "insert",
            smile.elapsedNanos(),
            smile.rows(),
            String.format(
                "batch INSERT  rows/s=%.0f rejected=%d",
                smile.rows() / (smile.elapsedNanos() / 1_000_000_000.0), smile.rejected())));
  }
}
