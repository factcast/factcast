package org.factcast.example.server.telemetry;

import com.google.common.eventbus.Subscribe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.telemetry.FactStreamTelemetryPublisher;
import org.factcast.store.internal.telemetry.FactStreamTelemetrySignal;

@RequiredArgsConstructor
@Slf4j
public class CatchupTelemetryListener {

  public CatchupTelemetryListener(FactStreamTelemetryPublisher publisher) {
    publisher.register(this);
  }

  @Subscribe
  public void on(FactStreamTelemetrySignal.Connect signal) {
    log.info("### FactStreamTelemetry Connect: {}", signal.request());
  }

  @Subscribe
  public void on(FactStreamTelemetrySignal.Catchup signal) {
    log.info("### FactStreamTelemetry Catchup: {}", signal.request());
  }

  @Subscribe
  public void on(FactStreamTelemetrySignal.FastForward signal) {
    log.info("### FactStreamTelemetry FastForward: {}", signal.request());
  }

  @Subscribe
  public void on(FactStreamTelemetrySignal.Follow signal) {
    log.info("### FactStreamTelemetry Follow: {}", signal.request());
  }

  @Subscribe
  public void on(FactStreamTelemetrySignal.Complete signal) {
    log.info("### FactStreamTelemetry Complete: {}", signal.request());
  }

  @Subscribe
  public void on(FactStreamTelemetrySignal.Close signal) {
    log.info("### FactStreamTelemetry Close: {}", signal.request());
  }
}
