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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    List<Fact> facts = new ArrayList<Fact>();

    for (int i = 0; i < 25000; i++) {
      final UUID id = UUID.randomUUID();
      Fact fact =
          Fact.builder()
              .ns("users")
              .type("UserCreated")
              .version(1)
              .id(id)
              .build("{\"firstName\":\"Horst\",\"lastName\":\"Lichter\"}");
      facts.add(fact);
    }

    fc.publish(facts);

    AtomicInteger count = new AtomicInteger(0);
    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated")).fromScratch(),
            f -> System.out.println(count.incrementAndGet()))
        .awaitCatchup();
  }
}
