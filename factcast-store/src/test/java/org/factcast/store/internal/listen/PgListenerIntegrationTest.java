/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.listen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.internal.notification.*;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Slf4j
@SpringJUnitConfig(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
@SuppressWarnings("all")
class PgListenerIntegrationTest {

  @Autowired EventBus eventBus;
  @Autowired FactStore factStore;
  @Autowired JdbcTemplate jdbc;

  @Nested
  class FactInsertTrigger {

    @Test
    @SneakyThrows
    void oneInsertNotificationPerTransaction() {
      FactInsertCollector collector = new FactInsertCollector(2);
      try {
        eventBus.register(collector);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        // RUN
        // publish together, should have same tx id
        factStore.publish(
            List.of(
                Fact.builder().ns("test").type("listenerTest1").id(id1).buildWithoutPayload(),
                Fact.builder().ns("test").type("listenerTest1").id(id2).buildWithoutPayload()));
        // separate, should have another tx id
        factStore.publish(
            List.of(Fact.builder().ns("test").type("listenerTest2").id(id3).buildWithoutPayload()));

        // so finally, there should be two notifications arriving as we have two txids
        assertThat(collector.await()).isTrue();

        assertThat(collector.signals())
            .hasSize(2)
            .anySatisfy(
                n -> {
                  assertThat(n.ns()).isEqualTo("test");
                  assertThat(n.type()).isEqualTo("listenerTest1");
                })
            .anySatisfy(
                n -> {
                  assertThat(n.ns()).isEqualTo("test");
                  assertThat(n.type()).isEqualTo("listenerTest2");
                });
      } finally {
        eventBus.unregister(collector);
      }
    }
  }

  @Nested
  class FactTruncateTrigger {

    @Test
    @SneakyThrows
    void notifiesTruncate() {
      FactTruncateCollector collector = new FactTruncateCollector(1);
      try {
        eventBus.register(collector);
        log.info(
            "Waiting in case there is a dangeling truncate notification caused by wiping the table before test start");
        collector.await(2, TimeUnit.SECONDS);
        collector.reset();

        log.debug("inserting a few facts");
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        // publish together, should have same tx id
        factStore.publish(
            List.of(
                Fact.builder().ns("test").type("listenerTest1").id(id1).buildWithoutPayload(),
                Fact.builder().ns("test").type("listenerTest1").id(id2).buildWithoutPayload()));
        // separate, should have another tx id
        factStore.publish(
            List.of(Fact.builder().ns("test").type("listenerTest2").id(id3).buildWithoutPayload()));

        assertThat(collector.signals()).isEmpty();

        // act
        log.info("truncating now");
        jdbc.execute("truncate fact");

        // assert
        assertThat(collector.await(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        eventBus.unregister(collector);
      }
    }
  }

  @Nested
  class FactUpdateTrigger {

    @Test
    @SneakyThrows
    void oneNotificationPerRowUpdated() {

      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      UUID id3 = UUID.randomUUID();

      factStore.publish(
          List.of(
              Fact.builder()
                  .ns("test")
                  .type("listenerTest1")
                  .id(id1)
                  .version(1)
                  .build("{\"value\":\"1\"}"),
              Fact.builder()
                  .ns("test")
                  .type("listenerTest1")
                  .id(id2)
                  .version(2)
                  .build("{\"value\":\"1\"}"),
              Fact.builder()
                  .ns("test")
                  .type("listenerTest2")
                  .id(id3)
                  .version(2)
                  .build("{\"value\":\"1\"}")));

      var collector = new FactUpdateCollector(2);
      eventBus.register(collector);
      try {
        // RUN
        jdbc.update(
            "UPDATE fact SET payload = '{\"value\":\"2\"}' WHERE header @> '{\"type\": \"listenerTest1\"}'"); // changing two rows

        // assert
        assertThat(collector.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(collector.signals())
            .hasSize(2)
            .anySatisfy(
                n -> {
                  assertThat(n.updatedFactId()).isEqualTo(id1);
                })
            .anySatisfy(
                n -> {
                  assertThat(n.updatedFactId()).isEqualTo(id2);
                });
      } finally {
        eventBus.unregister(collector);
      }
    }
  }
}

abstract class NotificationCollector<T extends StoreNotification> {

  private final Class<T> type;
  private final int initialLatchCount;
  private volatile CountDownLatch latch;
  @Getter private final List<T> signals = Collections.synchronizedList(new ArrayList<>());

  NotificationCollector(Class<T> type, int latchCount) {
    this.type = type;
    this.initialLatchCount = latchCount;
    this.latch = new CountDownLatch(latchCount);
  }

  @SuppressWarnings("unused")
  @Subscribe
  public void recordEvent(StoreNotification e) {
    if (type.isAssignableFrom(e.getClass()))
      synchronized (signals) {
        signals.add(type.cast(e));
        latch.countDown();
      }
  }

  boolean await() {
    return await(5, TimeUnit.SECONDS);
  }

  @SneakyThrows
  boolean await(int i, TimeUnit unit) {
    return latch.await(i, unit);
  }

  public void reset() {
    synchronized (signals) {
      signals.clear();
      latch = new CountDownLatch(initialLatchCount);
    }
  }
}

class FactInsertCollector extends NotificationCollector<FactInsertionNotification> {

  FactInsertCollector(int latchCount) {
    super(FactInsertionNotification.class, latchCount);
  }
}

class FactTruncateCollector extends NotificationCollector<FactTruncationNotification> {
  FactTruncateCollector(int latchCount) {
    super(FactTruncationNotification.class, latchCount);
  }
}

class FactUpdateCollector extends NotificationCollector<FactUpdateNotification> {
  FactUpdateCollector(int latchCount) {
    super(FactUpdateNotification.class, latchCount);
  }
}
