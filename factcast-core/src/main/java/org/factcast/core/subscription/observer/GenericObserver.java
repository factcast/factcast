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
 *
 * @param <T>
 */
public interface GenericObserver<I> {
    void onNext(I element);

    default void onCatchup() {
        // implement if you are interested in that event
    }

    default void onComplete() {
        // implement if you are interested in that event
    }

    default void onError(Throwable exception) {
        LoggerFactory.getLogger(GenericObserver.class).warn("Unhandled onError:", exception);
    }

    default FactObserver map(@NonNull Function<Fact, I> projection) {
        return new ObserverBridge<I>(this, projection);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class ObserverBridge<I> implements FactObserver {

        private final GenericObserver<I> delegate;

        private final Function<Fact, I> project;

        @Override
        public void onNext(Fact from) {
            delegate.onNext(project.apply(from));
        }

        @Override
        public void onCatchup() {
            delegate.onCatchup();
        }

        @Override
        public void onError(Throwable exception) {
            delegate.onError(exception);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }
}
