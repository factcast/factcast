package org.factcast.server.grpc.api;

import java.util.UUID;

import org.slf4j.LoggerFactory;

//TODO
public interface IdObserver {
	void onNext(UUID f);

	default void onCatchup() {
	}

	default void onComplete() {
	}

	default void onError(Throwable e) {
		LoggerFactory.getLogger(IdObserver.class).warn("Unhandled onError:", e);
	}
}
