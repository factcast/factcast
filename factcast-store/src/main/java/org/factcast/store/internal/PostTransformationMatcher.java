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
import java.util.function.*;
import java.util.stream.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.internal.filter.FactSpecMatcher;
import org.factcast.store.internal.script.JSEngineFactory;

/**
 * Predicate to filter Facts selected by the database query.
 *
 * <p>For PG, we can safely assume that only those rows are returned from the DB, that match the
 * queryable criteria. The only untested thing is the script-match which can be skipped, if no
 * FactSpec has a scripted filter.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Slf4j
public class PostTransformationMatcher implements Predicate<Fact> {

  @Getter
  @VisibleForTesting
  @Accessors(fluent = true)
  final boolean canBeSkipped;

  final List<FactSpecMatcher> matchers = new LinkedList<>();

  public PostTransformationMatcher(@NonNull SubscriptionRequest req, @NonNull JSEngineFactory ef) {
    boolean hasNoFilterScript = req.specs().stream().noneMatch(s -> s.filterScript() != null);
    boolean hasNoAggIdProperties =
        req.specs().stream().noneMatch(s -> s.aggIdProperties().isEmpty());
    if (hasNoFilterScript && hasNoAggIdProperties) {
      canBeSkipped = true;
      log.trace("{} post query filtering has been disabled", req);
    } else {
      canBeSkipped = false;
      this.matchers.addAll(req.specs().stream().map(s -> new FactSpecMatcher(s, ef)).toList());
    }
  }

  @Override
  public boolean test(Fact input) {
    return canBeSkipped || matchers.stream().anyMatch(m -> m.test(input));
  }
}
