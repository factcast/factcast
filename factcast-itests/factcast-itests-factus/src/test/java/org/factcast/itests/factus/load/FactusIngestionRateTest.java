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
package org.factcast.itests.factus.load;

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.itests.factus.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
public class FactusIngestionRateTest {
  private static final int BATCHES = 5000;
  private static final int BATCH_SIZE = 10000; // same as pickup

  static {
    System.setProperty("grpc.client.factstore.address", "static://localhost:9090");
  }

  @Autowired FactCast fc;
  @Autowired EventConverter ec;

  @Test
  public void loadTest() {

    List<Long> measurements = new ArrayList<>();

    for (int i = 0; i < BATCHES; i++) {
      List<Fact> facts = new ArrayList<>(BATCH_SIZE);
      for (int j = 0; j < BATCH_SIZE; j++) {
        facts.add(ec.toFact(new UserCreatedV1("Peterson", "Peter")));
      }
      measurements.add(
          measure(
              "publish batch",
              () -> {
                fc.publish(facts);
              }));
      log.info("avg: {}ms", measurements.stream().mapToLong(l -> l).average().orElse(-1));
    }
  }

  public long measure(String s, Runnable r) {
    var sw = Stopwatch.createStarted();
    r.run();
    Duration elapsed = sw.stop().elapsed();
    long millis = elapsed.toMillis();
    log.info("{} {}ms", s, millis);
    return millis;
  }
}
