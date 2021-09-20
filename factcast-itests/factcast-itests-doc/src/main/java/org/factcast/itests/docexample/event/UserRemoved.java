package org.factcast.itests.docexample.event;

import lombok.Data;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

import java.beans.ConstructorProperties;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Data
@Specification(ns = "user", type = "UserRemoved", version = 1)
public class UserRemoved implements EventObject {

    private final UUID userId;

    @ConstructorProperties("userId")
    public UserRemoved(UUID userId) {
        this.userId = userId;
    }

    @Override
    public Set<UUID> aggregateIds() {
        return Collections.emptySet();
    }
}
