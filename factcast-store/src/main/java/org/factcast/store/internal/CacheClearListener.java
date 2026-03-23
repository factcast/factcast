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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.notification.CacheClearNotification;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.transformation.cache.PgTransformationStoreChangeListener;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class CacheClearListener implements SmartInitializingSingleton, DisposableBean {
  private final EventBus bus;

  private final TransformationChains chains;
  private final SchemaRegistry registry;

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
  }

  /**
   * The caches that are currently <b>not</b> supposed to be cleared are:
   *
   * <ul>
   *   <li>PgTrasformationCache, as it might not be a good idea to get rid of all persisted
   *       transformations, also considering that the {@link PgTransformationStoreChangeListener}
   *       should take care of individual types invalidation
   *   <li>PgFactIdToSerialMapper, as it should not be affected by changes whatsoever
   * </ul>
   */
  @Subscribe
  public void on(CacheClearNotification ignore) {
    chains.clearCache();
    registry.clearNearCache();
  }

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
  }
}
