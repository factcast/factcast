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
package org.factcast.example.smilepoc.loader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.factcast.example.smilepoc.PocProperties;
import org.springframework.stereotype.Component;

/**
 * Reads a bounded sample of (header, payload) pairs straight from the CSV dump. These are the
 * verbatim JSON strings as they would be served over gRPC (the wire sends {@code fact.jsonHeader()}
 * / {@code fact.jsonPayload()} unchanged), so they are the faithful input for the wire-size and
 * re-serialization benchmarks — no database required. Loaded once and cached.
 */
@Slf4j
@Component
public class SampleLoader {

  private final PocProperties props;
  private List<Sample> cache;

  public SampleLoader(PocProperties props) {
    this.props = props;
  }

  public record Sample(String header, String payload) {}

  public synchronized List<Sample> load(int max) {
    if (cache != null && cache.size() >= max) {
      return cache;
    }
    Path csv = Path.of(props.csv().path());
    log.info("Loading up to {} header/payload samples from {}", max, csv);
    List<Sample> list = new ArrayList<>(max);
    try (BufferedReader reader = new BufferedReader(new FileReader(csv.toFile()));
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader)) {
      for (CSVRecord rec : parser) {
        if (list.size() >= max) {
          break;
        }
        String header = rec.get("header");
        String payload = rec.get("payload");
        if (header == null || payload == null || header.isBlank() || payload.isBlank()) {
          continue;
        }
        list.add(new Sample(header, payload));
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load samples from " + csv, e);
    }
    log.info("Loaded {} samples", list.size());
    cache = list;
    return cache;
  }
}
