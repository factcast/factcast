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
package org.factcast.store.internal.catchup.fetching;

import java.util.concurrent.atomic.*;
import lombok.Generated;
import lombok.NonNull;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.internal.query.CurrentStatementHolder;

@Generated
public class PgFetchingCatchUpFactory implements PgCatchupFactory {

  @NonNull final PgConnectionSupplier connectionSupplier;

  public PgFetchingCatchUpFactory(@NonNull PgConnectionSupplier connectionSupplier) {
    this.connectionSupplier = connectionSupplier;
  }

  @Override
  public PgFetchingCatchup create(
      @NonNull SubscriptionRequestTO request,
      @NonNull SubscriptionImpl subscription,
      @NonNull AtomicLong serial,
      @NonNull CurrentStatementHolder statementHolder) {
    return new PgFetchingCatchup(
        connectionSupplier, subscription, request, serial, statementHolder);
  }
}
