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

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.PgPostQueryMatcher;

@Slf4j
public class PgFactFilterImpl implements PgFactFilter {
  private final SubscriptionRequestTO request;
  private final PgBlacklist blacklist;
  private final PgPostQueryMatcher matcher;
  private final boolean skipTest;

  public PgFactFilterImpl(
      @NonNull SubscriptionRequestTO request,
      @NonNull PgBlacklist blacklist,
      @NonNull PgPostQueryMatcher matcher) {
    this.request = request;
    this.blacklist = blacklist;
    this.matcher = matcher;
    this.skipTest = matcher.canBeSkipped();
  }

  @Override
  public boolean test(Fact fact) {
    if (blacklist.isBlocked(fact.id())) {
      log.trace("{} filtered blacklisted id={}", request, fact.id());
      return false;
    }

    if (skipTest) return true;
    else return matcher.test(fact);
  }
}
