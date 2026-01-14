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

  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    final UUID id = UUID.randomUUID();
    Fact fact =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(1)
            .id(id)
            .build("{\"firstName\":\"Horst\",\"lastName\":\"Lichter\"}");
    fc.publish(fact);
    System.out.println("published " + fact);

    @NonNull Optional<Fact> uc = fc.fetchById(id);
    System.out.println(uc.get().jsonPayload());

    @NonNull Optional<Fact> uc1 = fc.fetchByIdAndVersion(id, 1);
    System.out.println(uc1.get().jsonPayload());

    @NonNull Optional<Fact> uc2 = fc.fetchByIdAndVersion(id, 2);
    System.out.println(uc2.get().jsonPayload());

    @NonNull Optional<Fact> uc3 = fc.fetchByIdAndVersion(id, 3);
    System.out.println(uc3.get().jsonPayload());

    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated").version(3))
                .fromScratch(),
            System.out::println)
        .awaitCatchup();

    fc.subscribe(
            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated").version(1))
                .fromScratch(),
            System.out::println)
        .awaitCatchup();

    // Follow subscription
    Subscription followSub =
        fc.subscribe(
            SubscriptionRequest.follow(FactSpec.ns("users").type("UserCreated").version(3))
                .fromScratch(),
            System.out::println);

    Fact anotherFact =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(3)
            .id(UUID.randomUUID())
            .build(
                "{\"firstName\":\"John\",\"lastName\":\"Wayne\",\"salutation\":\"Mr\",\"displayName\":\"JW\"}");
    fc.publish(anotherFact);
    System.out.println("published " + anotherFact);

    followSub.awaitCatchup(5000).close();

    UUID predefinedId = UUID.randomUUID();
    Subscription followSubAggId =
        fc.subscribe(
            SubscriptionRequest.follow(
                    FactSpec.ns("users").type("UserCreated").aggId(predefinedId).version(3))
                .fromScratch(),
            System.out::println);

    Fact predefinedIdFact =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(3)
            .id(UUID.randomUUID())
            .aggId(predefinedId)
            .build(
                "{\"firstName\":\"Dale\",\"lastName\":\"Cooper\",\"salutation\":\"Mr\",\"displayName\":\"Coop\"}");
    fc.publish(predefinedIdFact);
    System.out.println("published " + predefinedIdFact);

    fc.subscribe(
            SubscriptionRequest.catchup(
                    FactSpec.ns("users").type("UserCreated").aggId(predefinedId).version(3))
                .fromScratch(),
            System.out::println)
        .awaitCatchup();

    followSubAggId.awaitCatchup(5000).close();
  }
}
