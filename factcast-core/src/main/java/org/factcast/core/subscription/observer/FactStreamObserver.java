/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core.subscription.observer;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.store.internal.filter.FactStreamObserverGranularityException;

/**
 * internal abstraction wrapping any FactStreamObserver and providing the lenient interface needed
 * for any of them
 */
// this should be sealed, once we move the min jdk to 17
public class FactStreamObserver implements BaseFactStreamObserver {

  // ugly as hell.
  // TODO we should measure, if the instanceOf business has a **measurable** worse performance
  // profile

  private final BaseFactStreamObserver factStreamObserver;

  private final FactObserver factObserver;
  private final BatchingFactObserver batchingFactObserver;
  private final FlushingFactObserver flushingFactObserver;

  public FactStreamObserver(FactObserver factObserver) {
    this.factStreamObserver = factObserver;

    this.factObserver = factObserver;
    this.batchingFactObserver = null;
    this.flushingFactObserver = null;
  }

  public FactStreamObserver(BatchingFactObserver factObserver) {
    this.factStreamObserver = factObserver;

    this.factObserver = null;
    this.flushingFactObserver = null;
    this.batchingFactObserver = factObserver;
  }

  public FactStreamObserver(FlushingFactObserver factObserver) {
    this.factStreamObserver = factObserver;

    this.factObserver = null;
    this.flushingFactObserver = factObserver;
    this.batchingFactObserver = null;
  }

  protected FactStreamObserver(FactStreamObserver other) {
    this.factStreamObserver = other.factStreamObserver;

    this.factObserver = other.factObserver;
    this.flushingFactObserver = other.flushingFactObserver;
    this.batchingFactObserver = other.batchingFactObserver;
  }

  public void onNext(@Nullable Fact f) {
    if (factObserver != null) {
      factObserver.onNext(Objects.requireNonNull(f));
    } else if (flushingFactObserver != null) flushingFactObserver.onNext(f);
    else
      // questionable, if we mitigate or throw here
      throw new FactStreamObserverGranularityException(
          "We expect a onNext(List) call in order to delegate to a BatchingFactObserver.");
  }

  public void onNext(@NonNull List<Fact> facts) {
    if (batchingFactObserver != null) {
      batchingFactObserver.onNext(facts);
    } else if (factObserver != null) {
      facts.forEach(factObserver::onNext);
    } else if (flushingFactObserver != null) facts.forEach(flushingFactObserver::onNext);
  }

  @Override
  public void onFastForward(@NonNull FactStreamPosition pos) {
    factStreamObserver.onFastForward(pos);
  }

  @Override
  public void onFactStreamInfo(@NonNull FactStreamInfo info) {
    factStreamObserver.onFactStreamInfo(info);
  }

  @Override
  public void onComplete() {
    factStreamObserver.onComplete();
  }

  @Override
  public void onCatchup() {
    factStreamObserver.onCatchup();
  }

  @Override
  public void onError(@NonNull Throwable exception) {
    factStreamObserver.onError(exception);
  }
}
