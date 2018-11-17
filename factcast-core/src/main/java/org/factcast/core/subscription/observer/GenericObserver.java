/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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

import org.factcast.core.Fact;
import org.slf4j.LoggerFactory;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Callback interface to use when subscribing to Facts or Ids from a FactCast.
 *
 * see {@link IdObserver}, {@link FactObserver}
 *
 * @author uwe.schaefer@mercateo.com
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

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class ObserverBridge<I> implements FactObserver {

        private final GenericObserver<I> delegate;

        private final Function<Fact, I> project;

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
