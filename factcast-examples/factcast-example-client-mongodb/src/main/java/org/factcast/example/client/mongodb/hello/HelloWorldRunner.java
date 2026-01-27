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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.factus.Factus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;
  @NonNull Factus factus;

  @NonNull private MongoDatabase mongoDatabase;

  @NonNull private MongoDbSubscribedProjection subscription;

  @Override
  public void run(String... args) throws Exception {

    for (int i = 0; i < 10; i++) {
      val factId1 = UUID.randomUUID();
      val firstNameUuid = UUID.randomUUID();
      Fact created =
          Fact.builder()
              .ns("users")
              .type("UserCreated")
              .version(1)
              .id(factId1)
              .build("{\"firstName\":\"" + firstNameUuid + "\",\"lastName\":\"Lichter\"}");
      fc.publish(created);
    }

    factus.subscribe(subscription);

    ExecutorService threads = Executors.newFixedThreadPool(2);
    for (int i = 0; i < 2; i++) {
      threads.submit(
          () -> {
            log.info("Starting thread");
            final MongoDbProjection projection = new MongoDbProjection(mongoDatabase);
            factus.update(projection);
            log.info("Finished update at position: {}", projection.factStreamPosition());
            log.info("Total count users: {}", projection.findsAll().size());
            log.info("Done");
          });
    }
    threads.shutdown();

    // Check subscribed projection
    Thread.sleep(1000);
    log.info("Subscription position is {}", subscription.factStreamPosition());
  }
}
