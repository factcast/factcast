/*
 * Copyright © 2017-2025 factcast.org
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

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgFactStore;
import org.factcast.store.internal.notification.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnClass(PgFactStore.class)
public class StoreNotificationConflictAutoConfiguration implements InitializingBean {
  @Nullable private final StoreNotificationPublisher pub;
  @Nullable private final StoreNotificationSubscriber sub;

  @Override
  public void afterPropertiesSet() throws Exception {
    if (pub != null && sub != null) {
      String msg =
          "Dependency Conflict: You must not have both a store-sub* and a store-pub* dependency in your classpath. Either this instance should publish, or subscribe.";
      log.error(msg);
      throw new NotificationPubSubConflictException(msg);
    }
  }
}
