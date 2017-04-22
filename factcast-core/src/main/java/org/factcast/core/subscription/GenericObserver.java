package org.factcast.core.subscription;

import org.slf4j.LoggerFactory;

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
