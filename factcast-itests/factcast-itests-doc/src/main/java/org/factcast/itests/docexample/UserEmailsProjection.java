package org.factcast.itests.docexample;

import lombok.Getter;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.itests.docexample.event.UserAdded;
import org.factcast.itests.docexample.event.UserEmailChanged;
import org.factcast.itests.docexample.event.UserRemoved;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class UserEmailsProjection extends LocalManagedProjection {

    @Getter
    private Set<String> userEmails = new HashSet<>();

    @Handler
    void apply(UserAdded event) {
        userEmails.add(event.getEmail());
    }

    @Handler
    void apply(UserEmailChanged event) {
        userEmails.add(event.getEmail());
    }

    @Handler
    void apply(UserRemoved event) {
        userEmails.remove(event.getEmail());
    }
}
