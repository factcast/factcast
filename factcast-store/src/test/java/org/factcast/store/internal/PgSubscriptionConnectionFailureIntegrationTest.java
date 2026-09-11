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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.listen.PgListener;
import org.factcast.store.internal.notification.FactInsertionNotification;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {
      PgTestConfiguration.class,
      PgSubscriptionConnectionFailureIntegrationTest.Notifications.class
    })
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
// Close this test's own context so its connection pool does not stay open for the rest of the
// suite.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PgSubscriptionConnectionFailureIntegrationTest {
  @Autowired FactStore store;
  @Autowired EventBus notifications;
  @MockitoSpyBean PgConnectionSupplier connections;

  // Send notifications ourselves. Another notification could trigger a retry and hide the issue.
  @MockitoBean PgListener listener;

  @Test
  @Timeout(30)
  void shouldNotifySubscriberWhenFollowConnectionFails() throws Exception {
    String ns = "connection-failure-" + UUID.randomUUID();
    Fact initial = Fact.builder().ns(ns).buildWithoutPayload();
    Fact pending = Fact.builder().ns(ns).buildWithoutPayload();
    var observer = mock(FactObserver.class);
    var failure = new SQLException("Database temporarily unreachable", "08006");
    var request = SubscriptionRequest.follow(FactSpec.ns(ns)).fromScratch();

    store.publish(List.of(initial));
    try (var subscription = store.subscribe(SubscriptionRequestTO.from(request), observer)) {
      subscription.awaitCatchup(10000);
      await("initial event")
          .atMost(10, SECONDS)
          .untilAsserted(() -> verify(observer).onNext(argThat(f -> f.id().equals(initial.id()))));
      waitForFollowMode(ns);

      // Fail the next connection attempt. After that, the database is available again.
      doAnswer(
              invocation -> {
                throw failure;
              })
          .doCallRealMethod()
          .when(connections)
          .getPooledAsSingleDataSource(anyList());
      store.publish(List.of(pending));
      notifications.post(FactInsertionNotification.internal(ns, null));

      // The subscriber needs to know that something failed so it can reconnect.
      // Just logging the error leaves it waiting for the pending event.
      await("connection failure reaching the subscriber")
          .atMost(10, SECONDS)
          .untilAsserted(() -> verify(observer).onError(failure));
    }
  }

  private void waitForFollowMode(String ns) {
    // awaitCatchup can return before follow mode is ready. Wait for the first follow query.
    await("subscription entering follow mode")
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> verify(connections, atLeastOnce()).getPooledAsSingleDataSource(anyList()));
    // Finish that query before we set up the connection failure.
    notifications.post(FactInsertionNotification.internal(ns, null));
  }

  @Configuration
  static class Notifications {
    @Bean
    @Primary
    EventBus synchronousNotifications() {
      return new EventBus();
    }
  }
}
