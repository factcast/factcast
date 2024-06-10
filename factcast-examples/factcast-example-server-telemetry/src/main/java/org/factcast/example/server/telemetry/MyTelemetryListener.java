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

import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class MyTelemetryListener {

  final List<String> followingSubscriptionsInfo = Lists.newArrayList();

  public MyTelemetryListener(PgStoreTelemetry telemetry) {
    telemetry.register(this);
  }

  @Subscribe
  public void on(PgStoreTelemetry.Connect signal) {
    log.info("### FactStreamTelemetry Connect: {}", signal.request());
  }

  @Subscribe
  public void on(PgStoreTelemetry.Catchup signal) {
    log.info("### FactStreamTelemetry Catchup: {}", signal.request());
  }

  @Subscribe
  public void on(PgStoreTelemetry.Follow signal) {
    log.info("### FactStreamTelemetry Follow: {}", signal.request());
    followingSubscriptionsInfo.add(signal.request().debugInfo());
  }

  @Subscribe
  public void on(PgStoreTelemetry.Complete signal) {
    log.info("### FactStreamTelemetry Complete: {}", signal.request());
  }

  @Subscribe
  public void on(PgStoreTelemetry.Close signal) {
    log.info("### FactStreamTelemetry Close: {}", signal.request());
    followingSubscriptionsInfo.remove(signal.request().debugInfo());
  }

  public List<String> getFollowingSubscriptionsInfo() {
    return followingSubscriptionsInfo;
  }
}
