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
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.internal.notification.*;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Slf4j
@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
@SuppressWarnings("ResultOfMethodCallIgnored")
class PgListenerIntegrationTest {

  @Nested
  class FactInsertTrigger {

    @Autowired EventBus eventBus;
    @Autowired FactStore factStore;

    @Test
    @SneakyThrows
    void containsTransactionId() {
      EventCollector events = new EventCollector();
      try {
        eventBus.register(events);

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

        events.latch().await(5, TimeUnit.SECONDS);

        assertThat(events.signals())
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
        eventBus.unregister(events);
      }
    }

    @Getter
    public static class EventCollector {
      final List<FactInsertionNotification> signals = new ArrayList<>();

      @SuppressWarnings("unused")
      @Subscribe
      public void onEvent(FactInsertionNotification ev) {
        signals.add(ev);
        latch.countDown();
      }

      private final CountDownLatch latch = new CountDownLatch(2);
    }
  }

  @Nested
  class FactTruncateTrigger {

    @Autowired EventBus eventBus;
    @Autowired FactStore factStore;
    @Autowired JdbcTemplate jdbc;

    @Test
    @SneakyThrows
    @SuppressWarnings("unused")
    void listensToDatabase() {
      TruncateNotificationCollector ec = new TruncateNotificationCollector();
      try {
        eventBus.register(ec);

        // make sure we catch the initial truncate, even though it might have passed already
        log.info("Waiting up to three seconds to receive truncate event from test start");
        boolean mightHaveBeenTooLate = ec.latch().await(3, TimeUnit.SECONDS);
        ec.reset();

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

        assertThat(ec.latch().await(2, TimeUnit.SECONDS)).isFalse();

        // act
        log.info("truncating now");
        jdbc.execute("truncate fact");

        // assert
        assertThat(ec.latch().await(5, TimeUnit.SECONDS)).isTrue();
      } finally {
        eventBus.unregister(ec);
      }
    }

    @Getter
    public static class TruncateNotificationCollector {
      private CountDownLatch latch = new CountDownLatch(1);

      @SuppressWarnings("unused")
      @Subscribe
      public void onEvent(FactTruncationNotification ev) {
        latch.countDown();
      }

      public void reset() {
        latch = new CountDownLatch(1);
      }
    }
  }

  @Nested
  class FactUpdateTrigger {

    @Autowired EventBus eventBus;
    @Autowired FactStore factStore;
    @Autowired JdbcTemplate jdbc;

    @Test
    @SneakyThrows
    void containsTransactionId() {
      var events = new UpdateNotificationEventCollector();
      eventBus.register(events);

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

      // RUN
      jdbc.update(
          "UPDATE fact SET payload = '{\"value\":\"2\"}' WHERE header @> '{\"type\": \"listenerTest1\"}'"); // changing two rows

      // assert
      assertThat(events.latch().await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(events.signals())
          .hasSize(2)
          .anySatisfy(
              n -> {
                assertThat(n.updatedFactId()).isEqualTo(id1);
              })
          .anySatisfy(
              n -> {
                assertThat(n.updatedFactId()).isEqualTo(id2);
              });
    }

    @Getter
    public static class UpdateNotificationEventCollector {
      final List<FactUpdateNotification> signals = new ArrayList<>();

      @SuppressWarnings("unused")
      @Subscribe
      public void onEvent(FactUpdateNotification ev) {
        latch.countDown();
        signals.add(ev);
      }

      private final CountDownLatch latch = new CountDownLatch(2);
    }
  }
}
