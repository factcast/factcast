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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactCast;
import org.factcast.core.store.FactStore;
import org.factcast.factus.Factus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;

  @NonNull FactStore store;
  @NonNull Factus factus;

  @Override
  public void run(String... args) throws Exception {
    //
    //    val id = UUID.randomUUID();
    //    Fact fact =
    //        Fact.builder()
    //            .ns("users")
    //            .type("UserCreated")
    //            .version(1)
    //            .id(id)
    //            .build("{\"firstName\":\"Horst\",\"lastName\":\"Lichter\"}");
    //    fc.publish(fact);
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
    //    val uc3 = fc.fetchByIdAndVersion(id, 6);

    //    val uc3 = fc.fetchByIdAndVersion(id, 3);
    //    System.out.println(uc3.get().jsonPayload());
    //

    //
    //    // TODO tmp, to trigger event on metrics
    //
    //    SnapshotId sid = new SnapshotId("foo", UUID.randomUUID());
    //    @NonNull
    //    Snapshot snap =
    //        new Snapshot(sid, UUID.randomUUID(), "foo".getBytes(StandardCharsets.UTF_8), true);
    //    store.setSnapshot(snap);
    //    store.getSnapshot(sid);
    //    store.clearSnapshot(sid);
    //    store.currentTime();
    //    store.enumerateNamespaces();
    //    store.enumerateTypes("foo");

    //    CompletableFuture.runAsync(
    //        () -> {
    //          try {
    //            Thread.sleep(1000);
    //          } catch (InterruptedException e) {
    //            e.printStackTrace();
    //          }
    //          log.warn("publishing UC from Thread 2");
    //          factus.publish(new UserCreated("a", "b", "Mr", "d"));
    //        });
    //
    factus
        .withLockOn(UserNames.class)
        .attempt(
            (names, tx) -> {
              tx.publish(new UserCreated("a", "b", "Mr", "d"));
            });
  }
}
