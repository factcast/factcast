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
package org.factcast.example.tls.client.hello;

import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@SuppressWarnings("ALL")
@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    UUID aggId = UUID.fromString("50000000-4000-3000-2000-100000000000");

    Fact factToFind =
        Fact.builder().ns("smoke").type("foo").aggId(aggId).build("{\"bla\":\"fasel\"}");
    fc.publish(factToFind);
    for (int i = 0; i < 100_000; i++) {
      Fact fact =
          Fact.builder()
              .ns("smoke")
              .type("foo")
              .aggId(UUID.randomUUID())
              .build("{\"bla\":\"fasel\"}");
      fc.publish(fact);
      System.out.println("published " + fact);
    }

    for (int i = 0; i < 100; i++) {
      Subscription sub =
          fc.subscribe(
                  SubscriptionRequest.catchup(FactSpec.ns("smoke").aggId(aggId)).fromScratch(),
                  System.out::println)
              .awaitComplete(5000);
      sub.close();
    }
  }
}
