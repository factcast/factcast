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
package org.factcast.example.client.spring.boot2.hello;

import static java.lang.System.*;

import com.google.common.base.Stopwatch;
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

  private static final int MAX = 1000 * 1000;
  @NonNull private final FactCast fc;
  final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private Random random = new Random();

  @Override
  public void run(String... args) throws Exception {

    @NonNull List<Fact> facts = new LinkedList<>();
    out.println("Preparing");
    long milis = currentTimeMillis();
    for (int i = 0; i < MAX; i++) {
      Fact fact =
          Fact.builder()
              .ns("someNs" + random.nextInt(3))
              .type("someType" + random.nextInt(10))
              .version(1)
              .id(new UUID(milis, i))
              .build("{\"firstName\":\"Horst\",\"lastName\":\"Lichter\"}");
      facts.add(fact);
      //      if (i % 10000 == 0) {
      //        out.println("published " + i + " facts so far");
      //        if (!facts.isEmpty()) fc.publish(facts);
      //        facts.clear();
      //      } else {
      //        facts.add(fact);
      //      }

    }

    ExecutorService es = Executors.newFixedThreadPool(1024);
    out.println("Publishing");
    Stopwatch sw = Stopwatch.createStarted();
    facts.forEach(fact -> CompletableFuture.runAsync(() -> fc.publish(fact), es));
    es.shutdown();
    es.awaitTermination(10, TimeUnit.MINUTES);
    long ms = sw.stop().elapsed().toMillis();

    out.println(
        "published "
            + MAX
            + " facts in "
            + ms
            + "ms ("
            + ((MAX / (double) ms) * 1000)
            + "/second)");
  }

  private void publish(Fact fact) {
    jdbc.update(
        "insert into fact (header, payload) values (?::jsonb, ?::jsonb)",
        fact.jsonHeader(),
        fact.jsonPayload());
  }
}
