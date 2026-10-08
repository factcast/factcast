/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.store.internal.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.waitAtMost;

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
class PgStoreTelemetryIntegrationTest {

  @Autowired FactStore store;

  @Autowired PgStoreTelemetry telemetry;

  FactCast uut;

  @BeforeEach
  void setUp() {
    uut = FactCast.from(store);
  }

  @Test
  @SneakyThrows
  void publishesTelemetryOnCatchup() {
    var request =
        SubscriptionRequest.catchup(FactSpec.ns("foo").aggId(UUID.randomUUID())).fromScratch();
    var telemetryListener = new TelemetryListener(request);

    try (Subscription ignored = uut.subscribe(request, f -> {}).awaitComplete(5000)) {
      // wait until complete is consumed
      waitAtMost(Duration.ofSeconds(5)).until(() -> telemetryListener.completed);
    }

    // wait once more to ensure that all signals are consumed
    waitAtMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(telemetryListener.consumedSignals).size().isEqualTo(4);
              assertThat(telemetryListener.consumedSignals.get(0))
                  .isInstanceOf(PgStoreTelemetry.Connect.class);
              assertThat(telemetryListener.consumedSignals.get(1))
                  .isInstanceOf(PgStoreTelemetry.Catchup.class);
              assertThat(telemetryListener.consumedSignals.get(2))
                  .isInstanceOf(PgStoreTelemetry.Close.class);
              assertThat(telemetryListener.consumedSignals.get(3))
                  .isInstanceOf(PgStoreTelemetry.Complete.class);
            });
  }

  @Test
  @SneakyThrows
  void publishesTelemetryOnFollow() {
    var request =
        SubscriptionRequest.follow(FactSpec.ns("foo").aggId(UUID.randomUUID())).fromScratch();
    var telemetryListener = new TelemetryListener(request);

    try (Subscription ignored = uut.subscribe(request, f -> {}).awaitCatchup(5000)) {
      // wait until follow is consumed
      waitAtMost(Duration.ofSeconds(5)).until(() -> telemetryListener.following);
    }

    // wait once more to ensure that all signals are consumed
    waitAtMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(telemetryListener.consumedSignals).size().isEqualTo(4);
              assertThat(telemetryListener.consumedSignals.get(0))
                  .isInstanceOf(PgStoreTelemetry.Connect.class);
              assertThat(telemetryListener.consumedSignals.get(1))
                  .isInstanceOf(PgStoreTelemetry.Catchup.class);
              assertThat(telemetryListener.consumedSignals.get(2))
                  .isInstanceOf(PgStoreTelemetry.Follow.class);
              assertThat(telemetryListener.consumedSignals.get(3))
                  .isInstanceOf(PgStoreTelemetry.Close.class);
            });
  }

  class TelemetryListener {
    final List<Object> consumedSignals = Lists.newArrayList();
    final SubscriptionRequest origRequest;
    Boolean following = false;
    Boolean completed = false;

    TelemetryListener(@NonNull SubscriptionRequest origRequest) {
      this.origRequest = origRequest;
      telemetry.register(this);
    }

    @Subscribe
    void on(PgStoreTelemetry.Connect signal) {
      if (origRequest.specs().equals(signal.request().specs())) {
        consumedSignals.add(signal);
      }
    }

    @Subscribe
    void on(PgStoreTelemetry.Catchup signal) {
      if (origRequest.specs().equals(signal.request().specs())) {
        consumedSignals.add(signal);
      }
    }

    @Subscribe
    void on(PgStoreTelemetry.Follow signal) {
      if (origRequest.specs().equals(signal.request().specs())) {
        consumedSignals.add(signal);
        following = true;
      }
    }

    @Subscribe
    void on(PgStoreTelemetry.Complete signal) {
      if (origRequest.specs().equals(signal.request().specs())) {
        consumedSignals.add(signal);
        completed = true;
      }
    }

    @Subscribe
    void on(PgStoreTelemetry.Close signal) {
      if (origRequest.specs().equals(signal.request().specs())) {
        consumedSignals.add(signal);
      }
    }
  }
}
