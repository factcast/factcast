package org.factcast.store.internal.telemetry;


import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

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