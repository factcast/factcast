/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.filter;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.internal.PostQueryMatcher;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.factcast.store.internal.script.JSEngineFactory;

@Slf4j
public class FactFilterImpl implements FactFilter {
  private final SubscriptionRequest request;
  private final Blacklist blacklist;
  private final PostQueryMatcher matcher;

  public FactFilterImpl(
      @NonNull SubscriptionRequest request,
      @NonNull Blacklist blacklist,
      @NonNull JSEngineFactory ef) {
    this(request, blacklist, new PostQueryMatcher(request, ef));
  }

  @VisibleForTesting
  FactFilterImpl(
      @NonNull SubscriptionRequest request,
      @NonNull Blacklist blacklist,
      @NonNull PostQueryMatcher matcher) {
    this.request = request;
    this.blacklist = blacklist;
    this.matcher = matcher;
  }

  @Override
  public boolean test(@NonNull Fact fact) {
    if (blacklist.isBlocked(fact.id())) {
      log.trace("{} filtered blacklisted id={}", request, fact.id());
      return false;
    }

    if (matcher.canBeSkipped()) return true;
    else return matcher.test(fact);
  }
}
