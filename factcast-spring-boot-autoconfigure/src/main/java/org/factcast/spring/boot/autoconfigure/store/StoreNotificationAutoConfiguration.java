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

import lombok.extern.slf4j.Slf4j;
import org.factcast.store.pub.StoreNotificationPublisher;
import org.factcast.store.sub.StoreNotificationSubscriber;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnClass({StoreNotificationPublisher.class, StoreNotificationSubscriber.class})
public class StoreNotificationAutoConfiguration {
  @Bean
  public NotificationPubSubConflictException assertEitherPublishOrSubscribe() {
    throw new NotificationPubSubConflictException(
        "You must not have both a store-sub* and a store-pub* dependency in your classpath. Either this instance should publish, or subscribe.");
  }

  public static class NotificationPubSubConflictException extends IllegalStateException {
    NotificationPubSubConflictException(String msg) {
      super(msg);
    }
  }
}
