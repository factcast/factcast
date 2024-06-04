package org.factcast.example.server;

import com.google.common.eventbus.EventBus;
import org.factcast.example.server.telemetry.CatchupTelemetryListener;
import org.factcast.store.internal.telemetry.FactStreamTelemetryPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExampleServerTelemetryConfig {

  @Bean
  public CatchupTelemetryListener catchupTelemetryListener(FactStreamTelemetryPublisher publisher) {
    return new CatchupTelemetryListener(publisher);
  }
}
