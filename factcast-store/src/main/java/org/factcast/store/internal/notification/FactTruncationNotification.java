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

import javax.annotation.Nullable;
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
public class FactTruncationNotification extends StoreNotification {
  long txId;

  @Nullable
  public static FactTruncationNotification from(@NonNull PGNotification n) {
    return convert(n, json -> new FactTruncationNotification(txId(json)));
  }

  @Override
  public String uniqueId() {
    return PgConstants.CHANNEL_FACT_TRUNCATE + "-" + txId;
  }
}
