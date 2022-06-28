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
package org.factcast.factus;

import java.util.concurrent.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.factus.projection.WriterToken;

@RequiredArgsConstructor
class TokenAwareSubscription implements Subscription {
  @NonNull final Subscription delegate;
  @NonNull final WriterToken token;

  @Override
  public void close() throws Exception {
    try {
      delegate.close();
    } finally {
      token.close();
    }
  }

  @Override
  public Subscription awaitCatchup() throws SubscriptionClosedException {
    delegate.awaitCatchup();
    return this;
  }

  @Override
  public Subscription awaitCatchup(long waitTimeInMillis)
      throws SubscriptionClosedException, TimeoutException {
    delegate.awaitCatchup(waitTimeInMillis);
    return this;
  }

  @Override
  public Subscription awaitComplete() throws SubscriptionClosedException {
    delegate.awaitComplete();
    return this;
  }

  @Override
  public Subscription awaitComplete(long waitTimeInMillis)
      throws SubscriptionClosedException, TimeoutException {
    delegate.awaitComplete(waitTimeInMillis);
    return this;
  }
}
