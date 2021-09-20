package org.factcast.itests.docexample;

import lombok.Getter;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.itests.docexample.event.UserAdded;
import org.factcast.itests.docexample.event.UserEmailChanged;
import org.factcast.itests.docexample.event.UserRemoved;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class UserEmailsProjection extends LocalManagedProjection {

    private Map<UUID, String> userEmails = new HashMap<>();

    public Set<String> getEmails() {
        return new HashSet<>(userEmails.values());
    }

    @Handler
    void apply(UserAdded event) {
        userEmails.put(event.getUserId(), event.getEmail());
    }

    @Handler
    void apply(UserEmailChanged event) {
        userEmails.put(event.getUserId(), event.getEmail());
    }

    @Handler
    void apply(UserRemoved event) {
        userEmails.remove(event.getUserId());
    }
}
