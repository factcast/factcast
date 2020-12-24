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
package org.factcast.store.pgsql.internal.catchup.paged;

import java.util.concurrent.atomic.AtomicLong;
import lombok.Generated;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgPostQueryMatcher;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.listen.PgConnectionSupplier;

@RequiredArgsConstructor
// no code in here, just generated @nonnull checks
@Generated
public class PgPagedCatchUpFactory implements PgCatchupFactory {

  @NonNull final PgConnectionSupplier connectionSupplier;

  @NonNull final PgConfigurationProperties props;

  @Override
  public PgPagedCatchup create(
      @NonNull SubscriptionRequestTO request,
      @NonNull PgPostQueryMatcher postQueryMatcher,
      @NonNull SubscriptionImpl subscription,
      @NonNull AtomicLong serial) {
    return new PgPagedCatchup(
        connectionSupplier, props, request, postQueryMatcher, subscription, serial);
  }
}
