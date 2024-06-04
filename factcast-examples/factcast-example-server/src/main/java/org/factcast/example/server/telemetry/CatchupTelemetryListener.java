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
