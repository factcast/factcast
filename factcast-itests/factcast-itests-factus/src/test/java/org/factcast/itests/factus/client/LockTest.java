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
package org.factcast.itests.factus.client;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.film.IndianaJonesCharacterCreated;
import org.factcast.itests.factus.event.film.LucasNames;
import org.factcast.itests.factus.event.film.StarTrekCharacterCreated;
import org.factcast.itests.factus.event.film.StarWarsCharacterCreated;
import org.factcast.spring.boot.autoconfigure.snap.RedissonSnapshotCacheAutoConfiguration;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      TestFactusApplication.class,
      RedissonProjectionConfiguration.class,
      RedissonSnapshotCacheAutoConfiguration.class
    })
// in order to test with previous server version
// @FactcastTestConfig(factcastVersion = "0.9.9")
@Slf4j
class LockTest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  final AtomicInteger resetCounter = new AtomicInteger(0);
  final AtomicInteger totalPublishCounter = new AtomicInteger(0);

  @Test
  void multi() throws InterruptedException {
    List<CompletableFuture<?>> futures = new ArrayList<>(160_000);

    LucasNames characterCreated = new LucasNames();

    var before = System.currentTimeMillis();

    var id1 = UUID.randomUUID();
    var id3 = UUID.randomUUID();
    factus.publish(new StarTrekCharacterCreated(id1, "initial"));
    factus.publish(new StarWarsCharacterCreated(id3, "initial"));

    for (int i = 0; i < 200; i++) {
      totalPublishCounter.addAndGet(3);

      var f1 =
          CompletableFuture.runAsync(
              () ->
                  factus
                      .withLockOn(StarWarsAndStarTrekNamesWithId.class, id1)
                      .retries(1000)
                      .attempt(
                          (p, tx) -> {
                            resetCounter.incrementAndGet();
                            tx.publish(new StarWarsCharacterCreated(id1, "Luke"));
                          }));
      var f2 =
          CompletableFuture.runAsync(
              () ->
                  factus
                      .withLockOn(characterCreated)
                      .retries(1000)
                      .attempt(
                          (p, tx) -> {
                            resetCounter.incrementAndGet();
                            tx.publish(new IndianaJonesCharacterCreated("Indy"));
                          }));
      var f3 =
          CompletableFuture.runAsync(
              () ->
                  factus
                      .withLockOn(StarWarsAndStarTrekNamesWithId.class, id3)
                      .retries(1000)
                      .attempt(
                          (p, tx) -> {
                            resetCounter.incrementAndGet();
                            tx.publish(new StarTrekCharacterCreated(id3, "xx"));
                          }));
      futures.add(f1);
      futures.add(f2);
      futures.add(f3);
      Thread.sleep(30);
    }
    futures.forEach(CompletableFuture::join);
    var after = System.currentTimeMillis();
    System.out.println(((after - before) / 1000) + " seconds");
    System.out.println("resetCounter: " + (resetCounter.get() - totalPublishCounter.get()));
  }

  public static class StarWarsAndStarTrekNames extends LocalManagedProjection {

    @Handler
    void handle(StarWarsCharacterCreated e) {
      e.aggregateId();
    }

    @Handler
    void handle(StarTrekCharacterCreated e) {
      e.aggregateId();
    }
  }

  @ProjectionMetaData(name = "sw", revision = 1)
  public static class StarWarsAndStarTrekNamesWithId extends Aggregate {

    @Handler
    void handle(StarWarsCharacterCreated e) {
      e.aggregateId();
    }

    @Handler
    void handle(StarTrekCharacterCreated e) {
      e.aggregateId();
    }
  }

  public static class IndianaNames extends LocalManagedProjection {

    @Handler
    void handle(IndianaJonesCharacterCreated e) {
      e.aggregateId();
    }
  }
}
