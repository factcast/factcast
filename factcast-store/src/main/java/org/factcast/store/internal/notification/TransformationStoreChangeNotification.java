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

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgConstants;
import org.jetbrains.annotations.Nullable;
import org.postgresql.PGNotification;

@Value
@EqualsAndHashCode(callSuper = false)
@Slf4j
@SuppressWarnings("java:S1845")
public class TransformationStoreChangeNotification extends StoreNotification {
  @NonNull String ns;
  @NonNull String type;
  long txId;
  Integer fromVersion;
  Integer toVersion;

  public TransformationStoreChangeNotification(String ns, String type, long txId) {
    this(ns, type, txId, null, null);
  }

  public TransformationStoreChangeNotification(
      @NonNull String ns, @NonNull String type, long txId, Integer fromVersion, Integer toVersion) {
    this.ns = ns;
    this.type = type;
    this.txId = txId;
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
  }

  public static TransformationStoreChangeNotification from(PGNotification n) {
    return convert(
        n,
        json ->
            new TransformationStoreChangeNotification(
                ns(json),
                type(json),
                txId(json),
                json.hasNonNull("fromVersion") ? json.get("fromVersion").asInt() : null,
                json.hasNonNull("toVersion") ? json.get("toVersion").asInt() : null));
  }

  @Nullable
  @Override
  public String uniqueId() {
    // there might be multiple changes in on txId
    String id = PgConstants.CHANNEL_TRANSFORMATIONSTORE_CHANGE + "-" + ns + "-" + type + "-" + txId;
    return fromVersion == null || toVersion == null ? id : id + "-" + fromVersion + "-" + toVersion;
  }

  /**
   * still needs to be distributed, because we have a cache of the chains in
   * org.factcast.store.registry.transformation.chains.TransformationChains#cache that is cleared
   * when a PG notification for a given ns&type comes in.
   */
  @Override
  public boolean distributed() {
    return true;
  }
}
