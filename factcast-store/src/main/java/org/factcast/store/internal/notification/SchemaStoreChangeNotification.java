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
import javax.annotation.Nullable;
import lombok.*;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.PgConstants;
import org.postgresql.PGNotification;

@Value
@NonFinal
@Slf4j
public class SchemaStoreChangeNotification extends StoreNotification {

  @NonNull String ns;
  @NonNull String type;
  @NonNull int version;
  @Nullable Long txId;

  @Override
  public String uniqueId() {
    if (txId != null)
      return PgConstants.CHANNEL_SCHEMASTORE_CHANGE
          + "-"
          + ns
          + "-"
          + type
          + "-"
          + version
          + "-"
          + txId;
    else
      // something went wrong here, so we're cautious
      return null;
  }

  @Nullable
  public static SchemaStoreChangeNotification from(@NonNull PGNotification n) {
    try {
      JsonNode root = FactCastJson.readTree(n.getParameter());

      String ns = getString(root, "ns");
      String type = getString(root, "type");
      int version = getInt(root, "version");
      Long txId = getLong(root, "txId");

      return new SchemaStoreChangeNotification(ns, type, version, txId);

    } catch (JsonProcessingException | NullPointerException e) {
      // skipping
      log.warn("Unparseable JSON parameter from notification: {}.", n.getName());
    }
    return null;
  }
}
