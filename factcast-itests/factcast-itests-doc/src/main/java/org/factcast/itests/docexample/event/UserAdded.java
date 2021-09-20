package org.factcast.itests.docexample.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.Specification;

import java.beans.ConstructorProperties;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Data
@Specification(ns = "user", type = "UserAdded", version = 1)
public class UserAdded implements EventObject {

    private final UUID userId;
    private final String email;

    // hint Jackson deserializer
    @ConstructorProperties({"userId","email"})
    public UserAdded(UUID userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    @Override
    public Set<UUID> aggregateIds() {
        return Collections.emptySet();
    }
}
