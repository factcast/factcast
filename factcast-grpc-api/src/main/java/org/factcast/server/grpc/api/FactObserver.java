package org.factcast.server.grpc.api;

import org.factcast.core.Fact;
import org.slf4j.LoggerFactory;

//TODO
public interface FactObserver {
	void onNext(Fact f);

	default void onCatchup() {
	}

	default void onComplete() {
	}

	default void onError(Throwable e) {
		LoggerFactory.getLogger(FactObserver.class).warn("Unhandled onError:", e);
	}

}
