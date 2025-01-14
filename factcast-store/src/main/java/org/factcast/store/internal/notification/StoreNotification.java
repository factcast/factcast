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
package org.factcast.store.internal.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.FactCastJson;
import org.postgresql.PGNotification;

@Slf4j
public abstract class StoreNotification {
  /**
   * returning null here means that this signal must not be deduplicated
   *
   * @return id to dedup on
   */
  @Nullable
  abstract String uniqueId();

  /**
   * some Events are process internal so that
   *
   * @return true if the StoreEvent should be distributed to externalized infrastructure, *if* it is
   *     configured, false if it is supposed to be instance-only.
   */
  boolean distributed() {
    return true;
  }

  @VisibleForTesting
  @Nullable
  static Long extractTxId(@NonNull PGNotification n) {
    String json = n.getParameter();
    try {
      if (json != null) {
        JsonNode root = FactCastJson.readTree(json);
        return getLong(root, "txId");
      }
    } catch (JsonProcessingException e) {
      log.warn("unable to extract txId from notification of type {}", n.getName());
    }
    return null;
  }

  @VisibleForTesting
  static long getLong(@NonNull JsonNode root, @NonNull String name) {
    return root.get(name).asLong();
  }

  @VisibleForTesting
  static int getInt(@NonNull JsonNode root, @NonNull String name) {
    return root.get(name).asInt();
  }

  @VisibleForTesting
  static String getString(@NonNull JsonNode root, @NonNull String name) {
    return root.get(name).asText();
  }
}
