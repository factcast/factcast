package org.factcast.core.subscription;

import org.slf4j.LoggerFactory;

/**
 * Callback interface to use when subscribing to Facts or Ids from a FactCast.
 * 
 * see {@link IdObserver}, {@link FactObserver}
 * 
 * @author uwe.schaefer@mercateo.com
 *
 * @param <T>
 */
public interface GenericObserver<T> {
    void onNext(T element);

    default void onCatchup() {
    }

    default void onComplete() {
    }

    default void onError(Throwable exception) {
        LoggerFactory.getLogger(GenericObserver.class).warn("Unhandled onError:", exception);
    }

}
