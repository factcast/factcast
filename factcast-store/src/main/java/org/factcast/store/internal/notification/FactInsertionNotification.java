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

import jakarta.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.PgConstants;
import org.postgresql.PGNotification;

@Value
@EqualsAndHashCode(callSuper = false)
@NonFinal
@Slf4j
@SuppressWarnings("java:S1845")
public class FactInsertionNotification extends StoreNotification {
  @Nullable String ns;
  @Nullable String type;
  @Nullable Long ser;

  public static StoreNotification internal() {
    return new FactInsertionNotification(null, null, null) {
      @Override
      public boolean distributed() {
        return false;
      }
    };
  }

  @Override
  public String uniqueId() {
    if (ser == null) {
      return null; // no dedup wanted
    } else {
      // the ser is enough if it is not null
      return PgConstants.CHANNEL_FACT_INSERT + "-" + ser;
    }
  }

  @Override
  public boolean distributed() {
    return ser != null;
  }

  public static FactInsertionNotification from(@NonNull PGNotification pgNotification) {
    return convert(
        pgNotification, root -> new FactInsertionNotification(ns(root), type(root), ser(root)));
  }

  public String nsAndType() {
    // ser MUST NOT BE INCLUDED (dedup before posting)
    return ns + ":" + type;
  }
}
