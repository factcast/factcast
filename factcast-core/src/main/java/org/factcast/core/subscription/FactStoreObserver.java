package org.factcast.core.subscription;

import org.factcast.core.Fact;
import org.slf4j.LoggerFactory;

/**
 * Observer that consumes Facts
 *
 * @author usr
 *
 * @param <T>
 */
public interface FactStoreObserver {

    void onNext(Fact fact);

    default void onCatchup() {
    }

    default void onComplete() {
    }

    default void onError(Throwable e) {
        LoggerFactory.getLogger(FactStoreObserver.class).warn("Unhandled onError:", e);
    }

}
