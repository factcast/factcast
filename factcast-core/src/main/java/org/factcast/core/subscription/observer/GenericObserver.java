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
package org.factcast.core.subscription.observer;

import java.util.function.Function;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.slf4j.LoggerFactory;

/**
 * Callback interface to use when subscribing to Facts or Ids from a FactCast.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
public interface GenericObserver<I> {

  void onNext(@NonNull I element);

  default void onCatchup() {
    // implement if you are interested in that event
  }

  default void onComplete() {
    // implement if you are interested in that event
  }

  default void onError(@NonNull Throwable exception) {
    LoggerFactory.getLogger(GenericObserver.class).warn("Unhandled onError:", exception);
  }

  default FactObserver map(@NonNull Function<Fact, I> projection) {
    return new ObserverBridge<>(this, projection);
  }

  class ObserverBridge<I> implements FactObserver {

    private final GenericObserver<I> delegate;

    private final Function<Fact, I> project;

    protected ObserverBridge(GenericObserver<I> delegate, Function<Fact, I> project) {
      this.delegate = delegate;
      this.project = project;
    }

    @Override
    public void onNext(@NonNull Fact from) {
      delegate.onNext(project.apply(from));
    }

    @Override
    public void onCatchup() {
      delegate.onCatchup();
    }

    @Override
    public void onError(@NonNull Throwable exception) {
      delegate.onError(exception);
    }

    @Override
    public void onComplete() {
      delegate.onComplete();
    }
  }
}
