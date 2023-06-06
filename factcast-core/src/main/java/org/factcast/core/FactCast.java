/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import lombok.NonNull;
import org.factcast.core.lock.DeprecatedLockedOperationBuilder;
import org.factcast.core.lock.LockedOperationBuilder;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;

/**
 * Main interface to work against as a client.
 *
 * <p>FactCast provides methods to publish Facts in a sync/async fashion, as well as a subscription
 * mechanism to listen for changes and catching up.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public interface FactCast extends ReadFactCast {

  void publish(@NonNull List<? extends Fact> factsToPublish);

  // / ---------- defaults
  default void publish(@NonNull Fact factToPublish) {
    publish(Collections.singletonList(factToPublish));
  }

  static FactCast from(@NonNull FactStore store) {
    return new DefaultFactCast(store);
  }

  static ReadFactCast fromReadOnly(@NonNull FactStore store) {
    return new DefaultFactCast(store);
  }

  @Override
  // @Deprecated(since = "0.5.5", forRemoval = true)
  @Deprecated
  default FactCast retry(int maxAttempts) {
    return this;
  }

  @Override
  // @Deprecated(since = "0.5.5", forRemoval = true)
  @Deprecated
  default FactCast retry(int maxAttempts, long minimumWaitIntervalMillis) {
    return this;
  }

  LockedOperationBuilder lock(@NonNull List<FactSpec> scope);

  default LockedOperationBuilder lock(@NonNull FactSpec scope) {
    return lock(Collections.singletonList(scope));
  }

  default LockedOperationBuilder lock(@NonNull FactSpec scope, FactSpec... tail) {
    LinkedList<FactSpec> list = new LinkedList<FactSpec>();
    list.add(scope);
    list.addAll(Arrays.asList(tail));
    return lock(list);
  }

  /** @deprecated use lock(FactSpec) instead */
  // @Deprecated(forRemoval = true)
  @Deprecated
  DeprecatedLockedOperationBuilder lock(@NonNull String ns);
}
