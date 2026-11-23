/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PgMetricsTest {
  @Test
  void recordingFailuresNeverEscapeToPublicationCallers() {
    var registry = new SimpleMeterRegistry();
    try {
      registry
          .config()
          .meterFilter(
              new MeterFilter() {
                @Override
                public Meter.Id map(Meter.Id id) {
                  throw new IllegalStateException("registry unavailable");
                }
              });
      var metrics = new PgMetrics(registry);
      assertThatCode(
              () -> metrics.recordDuration(StoreMetrics.OP.PUBLICATION_TRANSACTION, 10, null))
          .doesNotThrowAnyException();
      assertThatCode(() -> metrics.recordValue(StoreMetrics.VALUE.PUBLICATION_BATCH_FACTS, 3))
          .doesNotThrowAnyException();
      assertThatCode(() -> metrics.increment(StoreMetrics.EVENT.QUEUED_FACTS_COMMITTED, 3))
          .doesNotThrowAnyException();
      assertThat(registry.getMeters()).isEmpty();
    } finally {
      registry.close();
    }
  }
}
