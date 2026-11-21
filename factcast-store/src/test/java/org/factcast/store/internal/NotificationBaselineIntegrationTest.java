/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.List;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.listen.*;
import org.factcast.store.internal.notification.*;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = PgTestConfiguration.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@IntegrationTest
class NotificationBaselineIntegrationTest {
  @Autowired FactStore writer;
  @Autowired JdbcTemplate jdbc;
  @Autowired PgListener listener;
  @Autowired NudgeNotificationHandler writerHandler;
  @Autowired PgMetrics metrics;

  @BeforeEach
  void stopAutomaticNotificationPolling() throws Exception {
    listener.destroy();
    writerHandler.destroy();
  }

  static class Wakeups {
    AtomicInteger count = new AtomicInteger();

    @Subscribe
    public void onInsertion(FactInsertionNotification n) {
      count.incrementAndGet();
    }
  }

  Fact fact() {
    return Fact.builder().id(UUID.randomUUID()).ns("review").type("test").buildWithoutPayload();
  }

  NudgeNotificationHandler readerHandler(EventBus bus) {
    StoreConfigurationProperties props = new StoreConfigurationProperties();
    props.setReadOnlyModeEnabled(true);
    // Only the explicit nudges should poll; neither the safety poll nor follow-ups may race them.
    try (var ignored = mockConstruction(Timer.class)) {
      return new NudgeNotificationHandler(bus, jdbc, props, metrics);
    }
  }

  @Test
  void missingNotificationBaselineStillWakesSubscribersWithoutNewNotificationMaximum()
      throws Exception {
    EventBus bus = new EventBus();
    Wakeups wakeups = new Wakeups();
    bus.register(wakeups);
    NudgeNotificationHandler handler = readerHandler(bus);
    try {
      writer.publish(List.of(fact()));
      handler.nudge(new NudgeNotification(1));
      assertThat(wakeups.count.get()).isEqualTo(1);
      writer.publish(List.of(fact()));
      jdbc.execute("TRUNCATE notification");
      int beforeRetry = wakeups.count.get();
      handler.nudge(new NudgeNotification(2));
      assertThat(wakeups.count.get())
          .as("reconnect with a lost notification baseline wakes all subscribers")
          .isGreaterThan(beforeRetry);
    } finally {
      handler.destroy();
    }
  }
}
