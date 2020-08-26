package org.factcast.factus.lock;

import java.util.HashSet;
import java.util.Set;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;

public class NamesProjection extends LocalManagedProjection {

    private final Set<String> names = new HashSet<>();

    public boolean contains(String name) {
        return names.contains(name);
    }

    @Handler
    void handle(UserCreated evt) {
        names.add(evt.name());
    }
}
