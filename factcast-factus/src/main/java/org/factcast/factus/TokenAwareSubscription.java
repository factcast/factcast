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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.*;
import org.factcast.factus.projection.WriterToken;

@RequiredArgsConstructor
@Slf4j
class TokenAwareSubscription implements InternalSubscription {
  @NonNull final InternalSubscription delegate;
  @NonNull final WriterToken token;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  @SneakyThrows
  @Override
  public void close() {
    if (!closed.getAndSet(true)) {
      try {
        delegate.close();
      } finally {
        token.close();
      }
    }
  }

  @Override
  public void notifyCatchup() {
    delegate.notifyCatchup();
  }

  @Override
  public void notifyFastForward(@NonNull UUID factId) {
    delegate.notifyFastForward(factId);
  }

  @Override
  public void notifyFactStreamInfo(@NonNull FactStreamInfo info) {
    delegate.notifyFactStreamInfo(info);
  }

  @Override
  public void notifyComplete() {
    delegate.notifyComplete();
  }

  @Override
  public void notifyError(Throwable e) {
    delegate.notifyError(e);
  }

  @Override
  public void notifyElement(@NonNull Fact e) throws TransformationException {
    delegate.notifyElement(e);
  }

  @Override
  public InternalSubscription onClose(Runnable e) {
    delegate.onClose(e);
    return this;
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
