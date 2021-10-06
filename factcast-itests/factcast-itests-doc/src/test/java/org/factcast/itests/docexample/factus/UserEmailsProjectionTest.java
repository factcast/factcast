package org.factcast.itests.docexample.factus;

import org.factcast.itests.docexample.factus.event.UserAdded;
import org.factcast.itests.docexample.factus.event.UserRemoved;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserEmailsProjectionTest {

    UserEmailsProjection uut = new UserEmailsProjection();
    UUID someUserId = UUID.randomUUID();

    @Test
    void whenHandlingUserAddedEventEmailIsAdded() {
        uut.apply(UserAdded.of(someUserId, "foo@bar.com"));
        Set<String> emails = uut.getEmails();

        assertThat(emails).hasSize(1).containsExactly("foo@bar.com");
    }

    @Test
    void whenHandlingUserRemovedEventEmailIsRemoved() {
        uut.apply(UserAdded.of(someUserId, "foo@bar.com"));
        uut.apply(UserRemoved.of(someUserId));

        Set<String> emails = uut.getEmails();
        assertThat(emails).isEmpty();
    }
}