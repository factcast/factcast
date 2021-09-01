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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.RandomStringUtils;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    publishFact(150_000);

    long sleep = 0;

    System.out.println("Done publishing, now reading...");
    Thread.sleep(sleep);
    System.out.println("Start");

    AtomicInteger c = new AtomicInteger(0);
    StopWatch s1 = new StopWatch();
    s1.start();
    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated").version(1))
                .fromScratch(),
            element -> {
              if (fastModulo(c.getAndIncrement(), 32_768) == 0) {
                System.out.println("Already processed " + c.get());
              }
            })
        .awaitCatchup();
    s1.stop();
    System.out.println("\n\n\n#####\n");
    System.out.println("Received v1 in seconds: " + s1.getTotalTimeSeconds());
    Thread.sleep(sleep);
    System.out.println("Start");

    c.set(0);
    StopWatch s2 = new StopWatch();
    s2.start();
    AtomicBoolean done = new AtomicBoolean(false);
    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated").version(3))
                .fromScratch(),
            element -> {
              if (!done.get()) {
                System.out.println(element.jsonHeader());
                done.set(true);
              }
              if (fastModulo(c.getAndIncrement(), 32_768) == 0) {
                System.out.println("Already processed " + c.get());
              }
            })
        .awaitCatchup();
    s2.stop();
    System.out.println("Received v3 in seconds: " + s2.getTotalTimeSeconds());
    c.set(0);
    Thread.sleep(sleep);
    System.out.println("Start");
    StopWatch s3 = new StopWatch();
    s3.start();
    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated").version(3))
                .fromScratch(),
            element -> {
              if (fastModulo(c.getAndIncrement(), 32_768) == 0) {
                System.out.println("Already processed " + c.get());
              }
            })
        .awaitCatchup();
    s3.stop();
    System.out.println("Received v3 from cache in seconds: " + s3.getTotalTimeSeconds());
  }

  public int fastModulo(int dividend, int divisor) {
    return dividend & (divisor - 1);
  }

  private void publishFact(long cnt) {

    int batchSize = 10_000;

    long rounds = cnt / batchSize;

    StopWatch sw = new StopWatch();
    sw.start();
    StopWatch swPublish = new StopWatch();
    for (int r = 0; r < rounds; r++) {
      List<Fact> facts = new ArrayList<>(batchSize);
      for (int b = 0; b < batchSize; b++) {

        Fact fact =
            Fact.builder()
                .ns("users")
                .type("UserCreated")
                .version(1)
                .id(UUID.randomUUID())
                .build(
                    "{\"firstName\":\""
                        + RandomStringUtils.randomAlphabetic(5, 10)
                        + "\",\"lastName\":\""
                        + RandomStringUtils.randomAlphabetic(5, 10)
                        + "\"}");
        facts.add(fact);
      }
      swPublish.start("round" + r);
      fc.publish(facts);
      swPublish.stop();
      System.out.print(".");
    }
    sw.stop();
    System.out.println("");
    System.out.println("Published batch took seconds: " + sw.getTotalTimeSeconds());
    System.out.println("Only publishing: " + swPublish.getTotalTimeSeconds());
  }
}
