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
package org.factcast.example.client.basicauth;

import java.util.Collections;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    Fact fact = Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}");
    fc.publish(fact);
    System.out.println("published " + fact);

    FactSpec factSpec = FactSpec.ns("smoke");

    Subscription sub =
        fc.subscribe(
                SubscriptionRequest.catchup(factSpec).fromScratch(),
                System.out::println)
            .awaitCatchup(5000);

    sub.close();

    System.out.println("Published facts so far in smoke: " + fc.countFacts(Lists.newArrayList(factSpec)));
    System.out.println("Published facts so far in total: " + fc.countFacts(Collections.emptyList()));
  }
}
