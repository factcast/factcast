package org.factcast.itests.docexample.event;

import lombok.Data;
import org.factcast.factus.event.EventObject;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Data
public class UserRemoved implements EventObject {

    private UUID userId;
    private String email;

    @Override
    public Set<UUID> aggregateIds() {
        return Collections.emptySet();
    }
}
