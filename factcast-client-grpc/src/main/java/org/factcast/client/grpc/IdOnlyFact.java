package org.factcast.client.grpc;

import java.util.Set;
import java.util.UUID;

import org.factcast.core.Fact;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class IdOnlyFact implements Fact {
    @Getter
    @NonNull
    final UUID id;

    @Override
    public String ns() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String type() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<UUID> aggIds() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String jsonHeader() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String jsonPayload() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String meta(String key) {
        throw new UnsupportedOperationException();
    }

}