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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BenchmarkFactsTest {
  @Test
  void generatesUniqueIdsAcrossPublishersAndSuccessiveBatches() {
    Set<UUID> ids = ConcurrentHashMap.newKeySet();
    IntStream.range(0, 256)
        .parallel()
        .forEach(
            publisher -> {
              var generator = new BenchmarkFacts(publisher);
              for (int call = 0; call < 3; call++) {
                var facts = generator.nextBatch(100);
                assertThat(facts).hasSize(100);
                facts.forEach(fact -> assertThat(ids.add(fact.id())).isTrue());
              }
            });
    assertThat(ids).hasSize(256 * 3 * 100);
  }

  @Test
  void generatesTheSameValidPayloadAndCoordinatesForBothBatchSizes() {
    var generator = new BenchmarkFacts(0);
    for (int batchSize : new int[] {1, 100}) {
      generator
          .nextBatch(batchSize)
          .forEach(
              fact -> {
                assertThat(fact.jsonPayload().getBytes(StandardCharsets.UTF_8)).hasSize(256);
                assertThat(fact.jsonPayload()).isEqualTo("{\"data\":\"" + "x".repeat(245) + "\"}");
                assertThat(fact.ns()).isEqualTo("concurrent-publish-benchmark");
                assertThat(fact.type()).isEqualTo("Published");
                assertThat(fact.version()).isEqualTo(1);
              });
    }
  }
}
