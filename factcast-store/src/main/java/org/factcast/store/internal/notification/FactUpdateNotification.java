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

import java.util.UUID;
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
public class FactUpdateNotification extends StoreNotification {
  @NonNull UUID updatedFactId;

  @Override
  public String uniqueId() {
    return PgConstants.CHANNEL_FACT_UPDATE + "-" + updatedFactId;
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

  public static FactUpdateNotification from(@NonNull PGNotification pgNotification) {
    return convert(pgNotification, root -> new FactUpdateNotification(factId(root)));
  }
}
