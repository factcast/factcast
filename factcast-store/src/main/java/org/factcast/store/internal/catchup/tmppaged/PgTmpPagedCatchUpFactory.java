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
package org.factcast.store.internal.catchup.tmppaged;

import java.util.concurrent.atomic.*;

import org.factcast.core.store.CascadingDisposal;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.blacklist.PgBlacklist;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.PgConnectionSupplier;

import lombok.Generated;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
// no code in here, just generated @nonnull checks
@Generated
public class PgTmpPagedCatchUpFactory implements PgCatchupFactory {

  @NonNull final PgConnectionSupplier connectionSupplier;

  @NonNull final StoreConfigurationProperties props;

  @Override
  public PgTmpPagedCatchup create(
      @NonNull SubscriptionRequestTO request,
      @NonNull PgPostQueryMatcher postQueryMatcher,
      @NonNull SubscriptionImpl subscription,
      @NonNull AtomicLong serial,
      @NonNull PgMetrics metrics,
      @NonNull PgBlacklist blacklist,
      @NonNull CascadingDisposal disposal) {
    return new PgTmpPagedCatchup(
        connectionSupplier,
        props,
        request,
        postQueryMatcher,
        subscription,
        serial,
        metrics,
        blacklist,
        disposal);
  }
}
