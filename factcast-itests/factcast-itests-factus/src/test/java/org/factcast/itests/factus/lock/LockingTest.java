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
package org.factcast.itests.factus.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.FactSpec;
import org.factcast.factus.Factus;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonProjectionConfiguration.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
public class LockingTest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  @Test
  public void lockOnFactSpecsOnlyHappyPath() {
    factus
        .withLockOn(FactSpec.ns("test").type("FooEvent"))
        .attempt(
            tx -> {
              tx.publish(new FooEvent());
            });
  }

  @SneakyThrows
  @Test
  public void lockOnFactSpecsOnlyWithRetry() {

    CountDownLatch cl = new CountDownLatch(1);
    AtomicInteger i = new AtomicInteger(0);

    CompletableFuture<Void> done =
        CompletableFuture.runAsync(
            () ->
                factus
                    .withLockOn(FactSpec.ns("test").type("FooEvent"))
                    .attempt(
                        tx -> {
                          cl.countDown();
                          i.incrementAndGet();
                          sleep(500);
                          tx.publish(new FooEvent());
                        }));

    cl.await();
    factus.publish(new FooEvent()); // should make the attempt retry
    done.get();

    assertThat(i.get()).isEqualTo(2);
  }

  @SneakyThrows
  @Test
  public void lockOnFactSpecsWithConcurrency() {

    CountDownLatch cl = new CountDownLatch(1);
    AtomicInteger i = new AtomicInteger(0);

    CompletableFuture<Void> done =
        CompletableFuture.runAsync(
            () ->
                factus
                    .withLockOn(FactSpec.ns("test").type("FooEvent"))
                    .attempt(
                        tx -> {
                          cl.countDown();
                          i.incrementAndGet();
                          sleep(500);
                          tx.publish(new FooEvent());
                        }));

    cl.await();
    factus.publish(new BarEvent()); // should not interfere
    done.get();

    assertThat(i.get()).isEqualTo(1);
  }

  @SneakyThrows
  static void sleep(long millis) {
    Thread.sleep(millis);
  }

  @Specification(ns = "test")
  public static class FooEvent implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return Collections.emptySet();
    }
  }

  @Specification(ns = "test")
  public static class BarEvent implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return Collections.emptySet();
    }
  }
}
