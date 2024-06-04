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
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.Test;

class FactStreamTelemetryPublisherTest {

  @Test
  void setsEventBusOnConstruction() {
    var eventBus = mock(EventBus.class);
    var listener = new TestListener();

    var uut = new FactStreamTelemetryPublisher(eventBus);
    uut.register(listener);

    verify(eventBus).register(listener);
  }

  @Test
  void delegatesPostToEventBus() throws InterruptedException {
    var signal = new FactStreamTelemetrySignal.Catchup(new SubscriptionRequestTO());
    var listener = new TestListener();
    var uut = new FactStreamTelemetryPublisher();
    uut.register(listener);

    uut.post(signal);

    assertThat(listener.latestSignal).isEqualTo(signal);
  }

  @Test
  void catchesExceptionsOnPost() {
    var eventBus = mock(EventBus.class);
    var signal = new FactStreamTelemetrySignal.Catchup(new SubscriptionRequestTO());
    var uut = new FactStreamTelemetryPublisher(eventBus);
    doThrow(new RuntimeException("expected")).when(eventBus).post(signal);

    assertThatNoException().isThrownBy(() -> uut.post(signal));
  }

  static class TestListener {
    FactStreamTelemetrySignal latestSignal;

    @Subscribe
    void on(FactStreamTelemetrySignal.Catchup signal) {
      this.latestSignal = signal;
    }
  }
}
