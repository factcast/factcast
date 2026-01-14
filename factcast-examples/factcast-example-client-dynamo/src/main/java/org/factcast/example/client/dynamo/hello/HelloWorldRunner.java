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

import com.google.common.collect.Lists;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    final var factId1 = UUID.randomUUID();
    final var firstNameUuid = UUID.randomUUID();
    Fact created =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(1)
            .id(factId1)
            .build("{\"firstName\":\"" + firstNameUuid + "\",\"lastName\":\"Lichter\"}");

    final var factId2 = UUID.randomUUID();
    Fact changed =
        Fact.builder()
            .ns("users")
            .type("UserChanged")
            .version(1)
            .id(factId2)
            .build("{\"firstName\":\"" + firstNameUuid + "\",\"lastName\":\"Lauch\"}");

    final var factId3 = UUID.randomUUID();
    final var firstNameUuid2 = UUID.randomUUID();
    Fact created2 =
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(1)
            .id(factId3)
            .build("{\"firstName\":\"" + firstNameUuid2 + "\",\"lastName\":\"August\"}");

    fc.publish(Lists.newArrayList(created, changed, created2));

    final DynamoProjection projection = new DynamoProjection(dynamoDbClient, "stateTable");
    factus.update(projection);
    log.info("Position is {}", projection.factStreamPosition());
  }
}
