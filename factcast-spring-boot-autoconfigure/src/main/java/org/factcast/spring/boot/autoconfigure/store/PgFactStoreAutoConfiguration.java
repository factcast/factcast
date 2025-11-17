/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.spring.boot.autoconfigure.store;

import com.google.common.eventbus.EventBus;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.PgFactStoreConfiguration;
import org.factcast.store.internal.*;
import org.factcast.store.internal.notification.StoreNotificationSubscriber;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@SuppressWarnings("resource")
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties
@ConditionalOnClass(PgFactStoreConfiguration.class)
@Import(PgFactStoreConfiguration.class)
public class PgFactStoreAutoConfiguration {
  // TODO this is no longer a good name.
  // When changing it however, we need to remember adapting dashboards, alarms & documentation
  private static final String EVENTBUS_IDENTIFIER = "pg-listener";

  // As defining the EventBus is now dependent on subscribers existing, this was moved to
  // autoconfiguration

  /** dedup is only necessary, if we have a subscriber. */
  @Bean
  @ConditionalOnClass(StoreNotificationSubscriber.class)
  @Primary
  public EventBus dedupEventBus(@NonNull PgMetrics metrics) {
    log.info(
        "A {} was detected. Deduplication eventBus. ",
        StoreNotificationSubscriber.class.getSimpleName());
    ThreadPoolExecutor listenerPool =
        new ThreadPoolExecutor(
            PgFactStoreInternalConfiguration.LISTENER_POOL_CORE_SIZE,
            PgFactStoreInternalConfiguration.LISTENER_POOL_MAX_SIZE,
            PgFactStoreInternalConfiguration.LISTENER_POOL_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>() // unbounded queue
            );
    listenerPool.allowCoreThreadTimeOut(true);
    return new DeduplicatingEventBus(
        DeduplicatingEventBus.class.getSimpleName(),
        metrics.monitor(listenerPool, EVENTBUS_IDENTIFIER));
  }
}
