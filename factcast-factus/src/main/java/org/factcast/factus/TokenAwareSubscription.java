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
import java.util.concurrent.atomic.*;

import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionClosedException;
import org.factcast.factus.projection.WriterToken;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
class TokenAwareSubscription implements Subscription {
  @NonNull final Subscription delegate;
  @NonNull final WriterToken token;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  @Override
  public void close() throws Exception {
    if (!closed.getAndSet(true)) {
      try {
        delegate.close();
      } finally {
        token.close();
      }
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
