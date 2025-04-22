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
package org.factcast.store.registry.transformation.cache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.notification.FactUpdateNotification;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class PgFactUpdateListener implements SmartInitializingSingleton, DisposableBean {

  private final EventBus bus;

  private final TransformationCache cache;

  private final TransformationChains chains;

  private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  static final long INFLIGHT_TRANSFORMATIONS_DELAY_SECONDS = 10L;

  @VisibleForTesting
  protected PgFactUpdateListener(
      EventBus bus,
      TransformationCache cache,
      TransformationChains chains,
      ScheduledExecutorService executor) {
    this.bus = bus;
    this.cache = cache;
    this.chains = chains;
    this.executor = executor;
  }

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
  }

  @Subscribe
  public void on(FactUpdateNotification signal) {
    invalidateCachesFor(signal);
    // schedule another cache invalidation
    // to avoid in-flight transformations to be persisted
    executor.schedule(
        () -> invalidateCachesFor(signal),
        INFLIGHT_TRANSFORMATIONS_DELAY_SECONDS,
        TimeUnit.SECONDS);
  }

  @VisibleForTesting
  void invalidateCachesFor(FactUpdateNotification signal) {
    cache.invalidateTransformationFor(signal.factId());
  }

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
  }
}
