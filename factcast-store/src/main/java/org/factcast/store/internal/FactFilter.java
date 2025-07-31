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
package org.factcast.store.internal;

import com.google.common.annotations.VisibleForTesting;
import java.util.*;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.internal.filter.*;
import org.factcast.store.internal.script.JSEngineFactory;

/**
 * Predicate to filter Facts selected by the database query.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
public class FactFilter implements PGFactMatcher {

  @VisibleForTesting
  @Accessors(fluent = true)
  private final boolean canBeSkipped;

  private final List<PGFactMatcher> matchers = new LinkedList<>();

  public FactFilter(@NonNull SubscriptionRequest req, @NonNull JSEngineFactory ef) {

    for (FactSpec spec : req.specs()) {
      // in order to test to true, we need to find ANY spec for which we match ALL matchers
      // (1A && 1B && 1C) || (2A && 2B) || ...

      @Nullable PGFactMatcher js = JSFilterScriptMatcher.matches(spec, ef);
      @Nullable PGFactMatcher aggID = AggIdPropertyMatcher.matches(spec);

      if (js != null || aggID != null) {
        matchers.add(PGFactMatcher.and(new BasicMatcher(spec), js, aggID));
      }
      // otherwise we skip filtering for this spec completely
    }

    if (matchers.isEmpty()) {
      log.trace("{} post query filtering has been disabled", req);
      canBeSkipped = true;
    } else {
      canBeSkipped = false;
    }
  }

  @Override
  public boolean test(PgFact input) {
    return canBeSkipped() || matchers.stream().anyMatch(m -> m.test(input));
  }

  public boolean canBeSkipped() {
    return this.canBeSkipped;
  }
}
