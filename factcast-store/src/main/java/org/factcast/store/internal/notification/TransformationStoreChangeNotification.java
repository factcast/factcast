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

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgConstants;
import org.jetbrains.annotations.Nullable;
import org.postgresql.PGNotification;

@Value
@Slf4j
public class TransformationStoreChangeNotification extends StoreNotification {
  @NonNull String ns;
  @NonNull String type;
  long txId;

  public static TransformationStoreChangeNotification from(PGNotification n) {
    return convert(
        n, json -> new TransformationStoreChangeNotification(ns(json), type(json), txId(json)));
  }

  @Nullable
  @Override
  String uniqueId() {
    // there might be multiple changes in on txId
    return PgConstants.CHANNEL_TRANSFORMATIONSTORE_CHANGE + "-" + ns + "-" + type + "-" + txId;
  }
}
