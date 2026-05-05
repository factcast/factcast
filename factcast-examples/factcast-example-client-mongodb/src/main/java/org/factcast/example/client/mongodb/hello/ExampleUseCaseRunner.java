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
package org.factcast.example.client.mongodb.hello;

import com.mongodb.client.MongoDatabase;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.example.client.mongodb.Examples;
import org.factcast.factus.Factus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExampleUseCaseRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;
  @NonNull Factus factus;

  @NonNull private MongoDatabase mongoDatabase;

  @NonNull private UserMongoDbSubscribedProjection subscribedProjection;
  @NonNull private UserMongoDbTxManagedProjection txManaged;

  @Override
  public void run(String... args) throws Exception {
    List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      var factId1 = UUID.randomUUID();
      var userId = UUID.randomUUID();
      facts.add(
          Fact.builder()
              .ns("users")
              .type("UserCreated")
              .version(1)
              .id(factId1)
              .aggId(userId)
              .build(
                  "{\"aggregateId\":\""
                      + userId
                      + "\",\"firstName\":\"Lena\",\"lastName\":\"Lichter\"}"));
    }
    final UUID firstUserId = facts.get(0).aggIds().stream().findFirst().get();
    fc.publish(facts);

    String scenario = Optional.ofNullable(args[0]).orElse("");
    log.info("Running scenario: {}", scenario);
    switch (Examples.valueOf(scenario)) {
      case MANAGED:
        managedProjectionSlowProcessing();
        break;
      case SUBSCRIBED:
        subscribedProjection();
        break;
      case TRANSACTIONAL:
        updateTxManagedProjection(firstUserId);
        transactionalManagedUserChanged(firstUserId);
        break;
      default:
        throw new IllegalArgumentException("Unknown scenario: " + scenario);
    }
  }

  private void managedProjectionSlowProcessing() {
    ExecutorService threads = Executors.newFixedThreadPool(2);
    for (int i = 0; i < 2; i++) {
      threads.submit(
          () -> {
            try {
              log.info("Starting thread");
              // Creating and updating managed projection
              final UserMongoDbManagedProjection slowProcessingManaged =
                  new UserMongoDbManagedProjection(mongoDatabase);
              factus.update(slowProcessingManaged);
              log.info(
                  "Managed - Finished update at position: {}",
                  slowProcessingManaged.factStreamPosition());
              log.info("Managed - Total count users: {}", slowProcessingManaged.findsAll().size());
            } catch (Exception e) {
              log.error(e.getMessage());
              throw e;
            }
          });
    }
    threads.shutdown();
  }

  @SneakyThrows
  private void subscribedProjection() {
    factus.subscribe(subscribedProjection);
    Thread.sleep(1000);
    log.info("Subscription position is {}", subscribedProjection.factStreamPosition());
  }

  @SneakyThrows
  private void updateTxManagedProjection(UUID firstUserId) {
    ExecutorService threads = Executors.newFixedThreadPool(2);
    for (int i = 0; i < 2; i++) {
      threads.submit(
          () -> {
            try {
              log.info("Starting thread");
              factus.update(txManaged);
              log.info("TxManaged - Finished at position: {}", txManaged.factStreamPosition());
              UserSchema user = txManaged.findByAggregateId(firstUserId);
              log.info("TxManaged - Last User: {}", user.getDisplayName());
              log.info("Done");

            } catch (Exception e) {
              log.error(e.getMessage());
              throw e;
            }
          });
    }
    threads.shutdown();
    threads.awaitTermination(2, TimeUnit.SECONDS);
  }

  private void transactionalManagedUserChanged(UUID firstUserId) {
    factus.publish(
        Fact.builder()
            .ns("users")
            .type("UserChanged")
            .version(1)
            .id(UUID.randomUUID())
            .aggId(firstUserId)
            .build(
                "{\"aggregateId\":\""
                    + firstUserId
                    + "\""
                    + ",\"firstName\":\"Lina\""
                    + ",\"lastName\":\"Zorro\"}"));

    factus.update(txManaged);
    UserSchema changedUser = txManaged.findByAggregateId(firstUserId);
    log.info("TxManaged - Changed User lastName: {}", changedUser.getLastName());
  }
}
