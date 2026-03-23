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
package org.factcast.store.internal.notification;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGNotification;

/**
 * Notification to invalidate the all near caches, useful when applying outstanding changes to the
 * store to avoid inconsistencies.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@Slf4j
@NonFinal
@AllArgsConstructor
@SuppressWarnings("java:S1845")
public class CacheInvalidateAllNotification extends StoreNotification {

  @Nullable
  public static CacheInvalidateAllNotification from(@NonNull PGNotification n) {
    return convert(n, json -> new CacheInvalidateAllNotification());
  }

  public static CacheInvalidateAllNotification internal() {
    return new CacheInvalidateAllNotification() {
      @Override
      public boolean distributed() {
        return false;
      }
    };
  }

  @Override
  public String uniqueId() {
    return null;
  }
}
