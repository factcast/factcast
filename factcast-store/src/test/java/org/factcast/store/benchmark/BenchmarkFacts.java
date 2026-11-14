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
package org.factcast.store.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.factcast.core.Fact;

final class BenchmarkFacts {
  private static final String PAYLOAD = "{\"data\":\"" + "x".repeat(245) + "\"}";
  private final long publisher;
  private long sequence;

  BenchmarkFacts(long publisher) {
    this.publisher = publisher;
  }

  List<Fact> nextBatch(int size) {
    List<Fact> facts = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      // Each JMH worker owns a distinct UUID prefix; the counter spans warmup and measurement.
      UUID id = new UUID(publisher, ++sequence);
      facts.add(
          Fact.of(
              "{\"id\":\""
                  + id
                  + "\",\"ns\":\"concurrent-publish-benchmark\",\"type\":\"Published\",\"version\":1}",
              PAYLOAD));
    }
    return facts;
  }
}
