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
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.FactCastJson;
import org.postgresql.PGNotification;

/**
 * Triggered from a PgNotification or by some internal behavior within the store, these
 * "Notifications" (sorry for the ambiguity) are passed around via an EventBus.
 *
 * <p>Also, they might by published to an external fanout if they return true from distributed().
 *
 * <p>(Only) In that case, the receiving store will publish them internally unless they have been
 * published already (a trail for uniqueIds will be kept to deduplicate).
 *
 * <p>If uniqueId() returns null, however, deduplication for this Notification will be skipped.
 */
@Slf4j
@SuppressWarnings("java:S1845")
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

  static long txId(JsonNode root) {
    return getLong(root, "txId");
  }

  static long ser(JsonNode root) {
    return getLong(root, "ser");
  }

  static int version(JsonNode root) {
    return getInt(root, "version");
  }

  static String ns(JsonNode root) {
    return getString(root, "ns");
  }

  static String type(JsonNode root) {
    return getString(root, "type");
  }

  static <T extends StoreNotification> T convert(
      PGNotification n, Function<JsonNode, T> initialization) {
    String json = n.getParameter();
    try {
      return initialization.apply(FactCastJson.readTree(json));
    } catch (JsonProcessingException | NullPointerException e) {
      // unparseable, probably longer than 8k ?
      // fall back to informingAllSubscribers
      log.warn("Unparseable JSON Parameter from Notification: {}.", n.getName());
      return null;
    }
  }

  private static long getLong(@NonNull JsonNode root, @NonNull String name) {
    return root.get(name).asLong();
  }

  private static int getInt(@NonNull JsonNode root, @NonNull String name) {
    return root.get(name).asInt();
  }

  private static String getString(@NonNull JsonNode root, @NonNull String name) {
    return root.get(name).asText();
  }
}
