package org.factcast.itests.docexample.factus;

import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.itests.docexample.factus.event.UserAdded;
import org.factcast.itests.docexample.factus.event.UserRemoved;

import java.util.*;

public class UserEmailsProjection extends LocalManagedProjection {

    private final Map<UUID, String> userEmails = new HashMap<>();

    public Set<String> getEmails() {
        return new HashSet<>(userEmails.values());
    }

    @Handler
    void apply(UserAdded event) {
        userEmails.put(event.getUserId(), event.getEmail());
    }

    @Handler
    void apply(UserRemoved event) {
        userEmails.remove(event.getUserId());
    }
}
