package org.factcast.core.subscription;

import org.slf4j.LoggerFactory;

/**
 * Callback interface to use when subscribing to Facts or Ids from a FactCast.
 * 
 * see {@link IdObserver}, {@link FactObserver}
 * 
 * @author usr
 *
 * @param <T>
 */
public interface GenericObserver<T> {
	void onNext(T f);

	default void onCatchup() {
	}

	default void onComplete() {
	}

	default void onError(Throwable e) {
		LoggerFactory.getLogger(GenericObserver.class).warn("Unhandled onError:", e);
	}

}
