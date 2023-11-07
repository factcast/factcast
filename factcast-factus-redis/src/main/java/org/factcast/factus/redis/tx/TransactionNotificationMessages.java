/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.factus.redis.tx;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.factus.redis.AbstractRedisSubscribedProjection;

@Getter
@RequiredArgsConstructor
public enum TransactionNotificationMessages {
  COMMIT("c"),
  ROLLBACK("r");

  @VisibleForTesting
  public static final String NOTIFICATION_TOPIC_POSTFIX = "_redis_transactional_notification";

  @NonNull private final String code;

  /**
   * @param p the redis projection for which we need the topic name
   * @return the full name of the topic into which we write the messages for this projection
   */
  public static String getTopicName(AbstractRedisSubscribedProjection p) {
    return p.getScopedName().with(NOTIFICATION_TOPIC_POSTFIX).asString();
  }

  public static TransactionNotificationMessages fromCode(String code) {
    for (TransactionNotificationMessages message : values()) {
      if (message.code.equals(code)) {
        return message;
      }
    }
    throw new IllegalArgumentException("Unknown code " + code);
  }
}
