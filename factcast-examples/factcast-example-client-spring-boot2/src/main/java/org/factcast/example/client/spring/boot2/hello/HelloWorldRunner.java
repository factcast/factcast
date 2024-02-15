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

import com.google.common.collect.Lists;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {
  private static final int MAX = 1000;
  private static final int SETS = 1;
  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    for (int j = 0; j < SETS; j++) {
      List<Fact> l = Lists.newArrayList();
      for (int i = 0; i < MAX; i++) {
        l.add(Fact.builder().ns("smoke").type("foo").version(1).build("{\"bla\":\"fasel\"}"));
      }
      fc.publish(l);
      System.out.println("published " + l.size() + " facts");
    }

    Subscription sub =
        fc.subscribe(
                SubscriptionRequest.follow(FactSpec.ns("smoke"))
                    .withMaxBatchDelayInMs(5000)
                    .fromScratch(),
                System.out::println)
            .awaitCatchup(5000);

    fc.publish(Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}"));
    fc.publish(Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}"));
    fc.publish(Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}"));

    Thread.sleep(5000);

    sub.close();
  }
}
