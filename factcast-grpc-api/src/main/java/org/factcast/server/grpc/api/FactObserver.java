package org.factcast.server.grpc.api;

import org.factcast.core.Fact;

public interface FactObserver extends GenericObserver<Fact> {
}