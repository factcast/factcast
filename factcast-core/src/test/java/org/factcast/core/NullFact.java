package org.factcast.core;

import java.util.Set;
import java.util.UUID;

import lombok.NonNull;

class NullFact implements Fact {
    @Override
    public @NonNull UUID id() {
        
        return null;
    }

    @Override
    public @NonNull String ns() {
        
        return null;
    }

    @Override
    public String type() {
        
        return null;
    }

    @Override
    public @NonNull Set<UUID> aggIds() {
        
        return null;
    }

    @Override
    public @NonNull String jsonHeader() {
        
        return null;
    }

    @Override
    public @NonNull String jsonPayload() {
        
        return null;
    }

    @Override
    public String meta(String key) {
        
        return null;
    }
}