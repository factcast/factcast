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
package org.factcast.example.client.dynamo.hello;

import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.factus.Factus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@RequiredArgsConstructor
@Component
@Slf4j
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;
  @NonNull Factus factus;

  @NonNull private DynamoDbClient dynamoDbClient;

  @Override
  public void run(String... args) throws Exception {

    val id = UUID.randomUUID();
    Fact fact =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(1)
            .id(id)
            .build("{\"firstName\":\"Horst\",\"lastName\":\"Lichter\"}");
    fc.publish(fact);
    System.out.println("published " + fact);
    //
    //    val uc = fc.fetchById(id);
    //    System.out.println(uc.get().jsonPayload());
    //
    //    val uc1 = fc.fetchByIdAndVersion(id, 1);
    //    System.out.println(uc1.get().jsonPayload());
    //
    //    val uc2 = fc.fetchByIdAndVersion(id, 2);
    //    System.out.println(uc2.get().jsonPayload());
    //
    //    val uc3 = fc.fetchByIdAndVersion(id, 3);
    //    System.out.println(uc3.get().jsonPayload());

    //    fc.subscribe(
    //            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated").version(3))
    //                .fromScratch(),
    //            element -> System.out.println(element))
    //        .awaitCatchup();
    //
    //    fc.subscribe(
    //            SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated").version(1))
    //                .fromScratch(),
    //            element -> System.out.println(element))
    //        .awaitCatchup();

    final DynamoProjection projection = new DynamoProjection(dynamoDbClient, "users", "stateTable");
    factus.update(projection);
    log.info("Position is {}", projection.factStreamPosition());
  }
}
